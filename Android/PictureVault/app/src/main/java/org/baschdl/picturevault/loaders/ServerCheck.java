package org.baschdl.picturevault.loaders;

import android.content.Context;
import android.os.AsyncTask;

import org.apache.commons.io.IOUtils;
import org.baschdl.picturevault.AppActivity;
import org.baschdl.picturevault.Server;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;

/**
 * Created by baschdl on 10.09.17.
 */

public class ServerCheck extends AsyncTask<String, Void, Boolean> {

    @Override
    protected Boolean doInBackground(String... strings) {
        HttpURLConnection connection = Server.connect(AppActivity.getContext(), strings[0], true);
        if (connection == null) return false;
        String time = Long.toString(System.currentTimeMillis());
        boolean out = false;

        try {
            IOUtils.write(time, connection.getOutputStream());
        } catch (IOException e) {
        }
        try {
            int responseCode = connection.getResponseCode();
            out = true;
        } catch (IOException e) {
        }
        connection.disconnect();
        return out;
    }
}
