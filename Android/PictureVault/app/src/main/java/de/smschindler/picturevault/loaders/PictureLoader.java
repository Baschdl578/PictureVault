package de.smschindler.picturevault.loaders;

import android.os.AsyncTask;

import de.smschindler.picturevault.MyApplication;
import de.smschindler.picturevault.Server;

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
