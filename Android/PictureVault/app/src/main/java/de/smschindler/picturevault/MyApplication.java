package de.smschindler.picturevault;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;

/**
 * Created by baschdl on 25.08.17.
 */

public class MyApplication extends Application {
    public static final String TAG = MyApplication.class.getSimpleName();

    private static MyApplication mInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
        new SetupTask().execute(getApplicationContext());
    }

    public static synchronized MyApplication getInstance() {
        return mInstance;
    }

    class SetupTask extends AsyncTask<Context, Void, Void> {
        @Override
        protected Void doInBackground(Context... contexts) {
            Utilities.setupSync(contexts[0]);
            Utilities.createNotificationChannels(contexts[0]);
            return null;
        }
    }
}