package de.smschindler.picturevault;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.view.SurfaceHolder;

import net.protyposis.android.mediaplayer.VideoView;
import net.protyposis.android.mediaplayer.dash.DashSource;
import net.protyposis.android.mediaplayer.dash.SimpleRateBasedAdaptationLogic;

import de.smschindler.picturevault.settings.SettingsManager;

import java.util.HashMap;

/**
 * Activity for testing stuff
 */
public class Test extends AppActivity {
    VideoView videoView;
    SurfaceHolder vidHolder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        videoView = (VideoView) findViewById(R.id.video);

        Uri videoUri = Uri.parse(
                "http://" + SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_SERVERADDRESS)
                        + ":" + SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_SERVERPORT)
                        + "/media/stream/" + 486 + "/mpd"
        );
        HashMap<String, String> headers = new HashMap<>();
        String userCredentials = SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_USERNAME)
                + ":" + SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_PASS);
        String basicAuth = "Basic " + Base64.encodeToString(userCredentials.getBytes(), Base64.NO_WRAP);
        headers.put("Authorization", basicAuth);

        //videoUri = Uri.parse("http://www-itec.uni-klu.ac.at/dash/js/content/bunny_ibmff_1080.mpd");

        new SourceConstructor().execute(videoUri);
/*
        DashSource source = new DashSource(getContext(), videoUri, headers, new SimpleRateBasedAdaptationLogic());

        videoView.setVideoSource(source);

        videoView.requestFocus();
        videoView.start(); /*

        //surface = (SimpleExoPlayerView) findViewById(R.id.video);

        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector =
                new DefaultTrackSelector(videoTrackSelectionFactory);

        mediaPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        HttpDataSource.RequestProperties properties = new HttpDataSource.RequestProperties();
        properties.set(headers);
        surface.setPlayer(mediaPlayer);
        DefaultHttpDataSourceFactory dataSourceFactory = new DefaultHttpDataSourceFactory(Util.getUserAgent(this, "ExoPlayer"));
        dataSourceFactory.setDefaultRequestProperty("Authorization", basicAuth);
        DashMediaSource dashMediaSource = new DashMediaSource(videoUri, dataSourceFactory,
                new DefaultDashChunkSource.Factory(dataSourceFactory), null, null);

        mediaPlayer.prepare(dashMediaSource);
*/
    }
/*
    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        // TODO Auto-generated method stub
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        mediaPlayer.setDisplay(vidHolder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        // TODO Auto-generated method stub
    }


    @Override
    public void onPrepared(MediaPlayer mp) {
        mediaPlayer.start();
    } */


    private class SourceConstructor extends AsyncTask<Uri, Void, DashSource> {
        @Override
        protected DashSource doInBackground(Uri... strings) {

            HashMap<String, String> headers = new HashMap<>();
            String userCredentials = SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_USERNAME)
                    + ":" + SettingsManager.getStringValue(getContext(), SettingsManager.SETTING_PASS);
            String basicAuth = "Basic " + Base64.encodeToString(userCredentials.getBytes(), Base64.NO_WRAP);
            headers.put("Authorization", basicAuth);
            return new DashSource(getContext(), strings[0], headers, new SimpleRateBasedAdaptationLogic());
        }

        @Override
        protected void onPostExecute(DashSource dashSource) {
            super.onPostExecute(dashSource);
            videoView.setVideoSource(dashSource);
            videoView.start();
        }
    }
}