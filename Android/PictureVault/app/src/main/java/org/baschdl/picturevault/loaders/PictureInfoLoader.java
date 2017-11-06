package org.baschdl.picturevault.loaders;

import android.os.AsyncTask;

import org.baschdl.picturevault.MyApplication;
import org.baschdl.picturevault.Server;
import org.baschdl.picturevault.model.Media;

/**
 * Created by baschdl on 23.08.17.
 */

public class PictureInfoLoader extends AsyncTask<Long, Void, Media> {

    @Override
    protected Media doInBackground(Long... longs) {
        return Server.getPictureInfo(MyApplication.getInstance().getApplicationContext(), longs[0]);
    }
}
