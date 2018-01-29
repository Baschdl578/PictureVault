package org.baschdl.picturevault.loaders;

import android.os.AsyncTask;

import org.baschdl.picturevault.MyApplication;
import org.baschdl.picturevault.Server;

import java.io.File;

/**
 * Loads a Thumbnail
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class ThumbLoader extends AsyncTask<Long, Void, File> {

    @Override
    protected File doInBackground(Long... longs) {
        return Server.getThumbnail(MyApplication.getInstance().getApplicationContext(), longs[0], null);
    }

}
