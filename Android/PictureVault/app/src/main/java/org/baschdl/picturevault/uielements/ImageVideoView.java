package org.baschdl.picturevault.uielements;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import net.protyposis.android.mediaplayer.FileSource;
import net.protyposis.android.mediaplayer.MediaPlayer;
import net.protyposis.android.mediaplayer.VideoView;
import net.protyposis.android.mediaplayer.dash.DashSource;
import net.protyposis.android.mediaplayer.dash.SimpleRateBasedAdaptationLogic;

import org.apache.commons.io.IOUtils;
import org.baschdl.picturevault.AppActivity;
import org.baschdl.picturevault.R;
import org.baschdl.picturevault.Server;
import org.baschdl.picturevault.loaders.PictureLoader;
import org.baschdl.picturevault.model.LibraryPicture;
import org.baschdl.picturevault.settings.SettingsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;

/**
 * View that is either an image or a video
 */
public class ImageVideoView extends FrameLayout {
    private SubsamplingScaleImageView imageView;
    private VideoView videoView;
    private RelativeLayout progressFrame;
    private ProgressBar progress;
    MediaController mediaController;
    Boolean isVideo;
    Boolean isReady;
    Boolean isVisible;
    Uri image;

    public ImageVideoView(Context context) {
        super(context);
        init(context);
    }

    public ImageVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ImageVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ImageVideoView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    public void init(Context context) {
        inflate(context, R.layout.element_imagevideo, this);
        imageView = findViewById(R.id.image);
        videoView = findViewById(R.id.video);
        progressFrame = findViewById(R.id.progressFrame);
        progress = findViewById(R.id.progress);
        mediaController = new MediaController(getContext());
        mediaController.setAnchorView(ImageVideoView.this);
        mediaController.setMediaPlayer((MediaController.MediaPlayerControl) videoView);
        mediaController.setEnabled(false);

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                isReady = true;
                if (isVisible)
                    videoView.start();
            }
        });

    }

    public void setElement(LibraryPicture element) {
        isReady = false;
        if (element.duration < 0) {
            isVideo = false;
            loadImage(element.id, element.name);
            return;
        }
        isVideo = true;
        if (SettingsManager.getBoolValue(getContext(), SettingsManager.SETTING_STREAMVID)) {
            streamVideo(element.id);
        } else {
            loadVideo(element.id, element.name, element.size);
        }
    }

    private void loadImage(Long id, String name) {
        progressFrame.setVisibility(GONE);
        videoView.setVisibility(GONE);
        new MyPictureLoader().execute(id.toString(), name);
    }

    private void streamVideo(Long id) {
        imageView.setVisibility(GONE);
        progressFrame.setVisibility(GONE);
        new DashSourceLoader().execute(id);
    }

    private void loadVideo(Long id, String name, Long size) {
        imageView.setVisibility(GONE);
        new MyVideoLoader().execute(id.toString(), name , size.toString());
    }

    public void stop() {
        isVisible = false;
        if (isVideo == null) return;
        if (isVideo) {
            videoView.stopPlayback();
        } else {
            imageView.setImage(ImageSource.resource(R.drawable.blank));
        }
    }

    public void start() {
        isVisible = true;
        if (isVideo == null) return;
        if (isVideo && isReady) {
            videoView.start();
        } else {
            if (isReady)
                imageView.setImage(ImageSource.uri(image));
        }
    }




    private class MyPictureLoader extends PictureLoader {

        @Override
        protected void onPostExecute(File file) {
            super.onPostExecute(file);
            if (file.exists()) {
                image = Uri.fromFile(file);
                isReady = true;
                imageView.setImage(ImageSource.uri(image));
            }
        }
    }

    private class DashSourceLoader extends AsyncTask<Long, Void, DashSource> {
        @Override
        protected DashSource doInBackground(Long... longs) {
            Uri videoUri = Uri.parse(
                    "http://" + SettingsManager.getStringValue(AppActivity.getContext(), SettingsManager.SETTING_SERVERADDRESS)
                            + ":" + SettingsManager.getStringValue(AppActivity.getContext(), SettingsManager.SETTING_SERVERPORT)
                            + "/media/stream/" + longs[0].toString() + "/mpd"
            );
            HashMap<String, String> headers = new HashMap<>();
            String userCredentials = SettingsManager.getStringValue(AppActivity.getContext(), SettingsManager.SETTING_USERNAME)
                    + ":" + SettingsManager.getStringValue(AppActivity.getContext(), SettingsManager.SETTING_PASS);
            String basicAuth = "Basic " + Base64.encodeToString(userCredentials.getBytes(), Base64.NO_WRAP);
            headers.put("Authorization", basicAuth);

            return new DashSource(AppActivity.getContext(), videoUri, headers, new SimpleRateBasedAdaptationLogic());
        }

        @Override
        protected void onPostExecute(DashSource dashSource) {
            super.onPostExecute(dashSource);
            mediaController.setEnabled(true);
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

                cacheDir = new File(AppActivity.getContext().getCacheDir(), filename);

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
            String addr = "http://" + SettingsManager.getStringValue(AppActivity.getContext(), SettingsManager.SETTING_SERVERADDRESS) + ":" + SettingsManager.getStringValue(AppActivity.getContext(), SettingsManager.SETTING_SERVERPORT) + addrpart;
            HttpURLConnection connection = Server.connect(AppActivity.getContext(), addr);
            if (connection == null) return null;

            try {
                IOUtils.write(id.toString(), connection.getOutputStream());
            } catch (IOException e) {
                Log.i(Server.class.getName(), "IOException while writing into connection: " + e.getMessage());
            }

            try {
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    Server.handleError(connection);
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
            progress.setMax(1000000);
            progress.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(FileSource file) {
            super.onPostExecute(file);
            if (file == null) return;
            progressFrame.setVisibility(View.GONE);
            mediaController.setEnabled(true);
            videoView.setVideoSource(file);
        }
    }

}
