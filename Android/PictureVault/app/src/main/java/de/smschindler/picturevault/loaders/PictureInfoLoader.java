package de.smschindler.picturevault.loaders;

import android.os.AsyncTask;

import de.smschindler.picturevault.MyApplication;
import de.smschindler.picturevault.Server;
import de.smschindler.picturevault.model.Media;

/**
 * Created by baschdl on 23.08.17.
 */

public class PictureInfoLoader extends AsyncTask<Long, Void, Media> {

    @Override
    protected Media doInBackground(Long... longs) {
        return Server.getPictureInfo(MyApplication.getInstance().getApplicationContext(), longs[0]);
    }
}
