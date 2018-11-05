package de.smschindler.picturevault.image;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;

import net.protyposis.android.mediaplayer.FileSource;
import net.protyposis.android.mediaplayer.MediaPlayer;
import net.protyposis.android.mediaplayer.VideoView;
import net.protyposis.android.mediaplayer.dash.DashSource;
import net.protyposis.android.mediaplayer.dash.SimpleRateBasedAdaptationLogic;

import org.apache.commons.io.IOUtils;
import de.smschindler.picturevault.R;
import de.smschindler.picturevault.Server;
import de.smschindler.picturevault.settings.SettingsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;

/**
 * Fragment that displays a video
 */
public class VideoFragment extends Fragment {

    public static final String ARG_ID = "id";
    public static final String ARG_NAME = "name";
    public static final String ARG_SIZE = "size";
    public static final String ARG_VIDEOPOS = "videopos";

    Long id;
    String name;
    Long size;

    private VideoView videoView;
    private LinearLayout progressFrame;
    private ProgressBar downloadProgress;
    private boolean prepared;
    private Float initialY;
    MediaController mediaController;
    private Integer videoPosition;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(
                R.layout.fragment_video, container, false);
        if (savedInstanceState != null) {
            videoPosition = savedInstanceState.getInt(ARG_VIDEOPOS);
        } else {
            videoPosition = -1;
        }

            Bundle args = getArguments();
            id = args.getLong(ARG_ID);
            name = args.getString(ARG_NAME);
            size = args.getLong(ARG_SIZE);


        prepared = false;
        downloadProgress = rootView.findViewById(R.id.progress);
        progressFrame = rootView.findViewById(R.id.progressFrame);
        videoView = rootView.findViewById(R.id.video);

        mediaController = new MediaController(getContext());
        mediaController.setAnchorView(rootView);
        mediaController.setMediaPlayer((MediaController.MediaPlayerControl) videoView);
        mediaController.setEnabled(false);

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                setPrepared();
                mediaController.setEnabled(true);
                mediaController.show(3000);
                if (getUserVisibleHint()) {
                    if (videoPosition > 0)
                        videoView.seekTo(videoPosition);

                    videoView.start();
                }
            }
        });

        View overlay = rootView.findViewById(R.id.overlay);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isPrepared())
                    mediaController.show(3000);
            }
        });

        overlay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    initialY = motionEvent.getY();
                }
                if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    float newY = motionEvent.getY();
                    if (initialY == null) return false;
                    if (newY < initialY) {
                        if (!((FullImage) getActivity()).behaviorIsCollapsed()) {
                            ((FullImage) getActivity()).showSheet();
                        } else {
                            ((FullImage) getActivity()).expandSheet();
                        }
                        return false;
                    }
                    if (newY > initialY) {
                        if (((FullImage) getActivity()).behaviorIsCollapsed()) {
                            ((FullImage) getActivity()).hideSheet();
                        }
                    }
                }
                return false;
            }
        });
        if (savedInstanceState == null) {

            if (SettingsManager.getBoolValue(getContext(), SettingsManager.SETTING_STREAMVID)) {
                progressFrame.setVisibility(View.GONE);
                new DashSourceLoader().execute(id);
            } else {
                new MyVideoLoader().execute(id.toString(), name, size.toString());
            }
        } else {
            if (SettingsManager.getBoolValue(getContext(), SettingsManager.SETTING_STREAMVID)) {
                progressFrame.setVisibility(View.GONE);
                new DashSourceLoader().execute(id);
            }
        }

        return rootView;
    }

    private synchronized void setPrepared() {
        prepared = true;
    }

    private synchronized boolean isPrepared() {
        return prepared;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (videoView != null && isPrepared())
            outState.putInt(ARG_VIDEOPOS, videoView.getCurrentPosition());

    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            start();
        } else {
            stop();
        }
    }

    public void stop() {
        if (videoView != null) {
            videoView.pause();
        }
    }

    public void start() {
        if (isPrepared() && videoView != null) {
            videoView.start();
        }
    }

    private class DashSourceLoader extends AsyncTask<Long, Void, DashSource> {
        @Override
        protected DashSource doInBackground(Long... longs) {
            Uri videoUri = Uri.parse(
                    "http://" + SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_SERVERADDRESS)
                            + ":" + SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_SERVERPORT)
                            + "/media/stream/" + longs[0].toString() + "/mpd"
            );
            HashMap<String, String> headers = new HashMap<>();
            String userCredentials = SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_USERNAME)
                    + ":" + SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_PASS);
            String basicAuth = "Basic " + Base64.encodeToString(userCredentials.getBytes(), Base64.NO_WRAP);
            headers.put("Authorization", basicAuth);

            return new DashSource(getContext(), videoUri, headers, new SimpleRateBasedAdaptationLogic());
        }

        @Override
        protected void onPostExecute(DashSource dashSource) {
            super.onPostExecute(dashSource);
            videoView.setVideoSource(dashSource);
        }
    }

    private class MyVideoLoader extends AsyncTask<String, Integer, FileSource> {


        @Override
        protected FileSource doInBackground(String... strings) {
            Log.i("DEBUG", "Loading video");
            File out = null;
            Long length = Long.parseLong(strings[2]);
            Long id = Long.parseLong(strings[0]);
            String name = strings[1];
            String filename = id.toString() + "/";
            File cacheFile = null;
            File cacheDir = null;
            try {

                cacheDir = new File(getContext().getCacheDir(), filename);

                cacheFile = new File(cacheDir, name);

                if (cacheFile.isFile() && cacheFile.exists()) {
                    if (cacheFile.length() == length) {
                        return new FileSource(cacheFile);
                    } else {
                        cacheFile.delete();
                    }
                }
            } catch (NullPointerException e) {
                return null;
            }


            String addrpart = "/media/load";
            String addr = "http://" + SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_SERVERPORT) + addrpart;
            HttpURLConnection connection = Server.connect(getContext(), addr);
            if (connection == null) return null;

            try {
                IOUtils.write(id.toString(), connection.getOutputStream());
            } catch (IOException e) {
                Log.i(Server.class.getName(), "IOException while writing into connection: " + e.getMessage());
            }

            try {
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    //Server.handleError(connection);
                } else {
                    if (!cacheDir.mkdirs()) {
                        Log.i(Server.class.getName(), "Could not create cache dir");
                    }
                    if (!cacheFile.createNewFile()) {
                        Log.i(Server.class.getName(), "Could not create cache dir");
                    }
                    FileOutputStream fOut = new FileOutputStream(cacheFile);
                    try {
                        byte[] buffer = new byte[4096];
                        int read = connection.getInputStream().read(buffer);
                        Long progress = 0L;
                        while (read > 0) {
                            fOut.write(buffer, 0, read);
                            progress += read;
                            Long tmp = progress * 1000000;
                            tmp /= length;
                            publishProgress(tmp.intValue());
                            read = connection.getInputStream().read(buffer);
                        }
                    } catch (IOException e) {
                        Log.i(Server.class.getName(), "IOException while writing to file: " + e.getMessage());

                    }
                    out = cacheFile;
                }
            } catch (IOException e) {
                Log.i(Server.class.getName(), "IOException while reading response : " + e.getMessage());

            }
            connection.disconnect();
            return new FileSource(out);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            downloadProgress.setMax(1000000);
            downloadProgress.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(FileSource file) {
            super.onPostExecute(file);
            if (file == null) return;
            progressFrame.setVisibility(View.GONE);

            videoView.setVideoSource(file);
        }
    }
}
