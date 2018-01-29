package org.baschdl.picturevault.loaders;

import android.os.AsyncTask;

import org.baschdl.picturevault.MyApplication;
import org.baschdl.picturevault.Server;

import java.io.File;

/**
 * Loads a picture File
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class PictureLoader extends AsyncTask<String, Void, File> {

    @Override
    protected File doInBackground(String... args) {
        return Server.getMedia(MyApplication.getInstance().getApplicationContext(), Long.parseLong(args[0]), args[1]);
    }
}
