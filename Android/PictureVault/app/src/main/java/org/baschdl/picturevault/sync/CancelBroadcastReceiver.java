package org.baschdl.picturevault.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives Broadcasts that cancel the sync operation
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class CancelBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        MediaSync instance = MediaSync.getInstance(context);
        if (instance != null && instance.isRunning())
            instance.cancel();
    }
}
