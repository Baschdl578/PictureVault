package org.baschdl.picturevault.loaders;

import android.os.AsyncTask;

import org.baschdl.picturevault.AppActivity;
import org.baschdl.picturevault.Server;

/**
 * Created by baschdl on 10.09.17.
 */

public class PulseCheck extends AsyncTask<Void, Void, Boolean> {

    @Override
    protected Boolean doInBackground(Void... voids) {
        return Server.checkPulse(AppActivity.getContext());
    }
}
