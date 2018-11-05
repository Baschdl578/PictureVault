package de.smschindler.picturevault.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;

/**
 * Created by baschdl on 26.08.17.
 */

public class MediaSyncAdapter extends AbstractThreadedSyncAdapter {

    /**
     * Performs Sync
     *
     * @param account    Acoount (stub)
     * @param extras     Extras
     * @param authority  Authority (unused?)
     * @param provider   ContentProvider (stub)
     * @param syncResult Result
     */
    @Override
    public void onPerformSync(
            Account account,
            Bundle extras,
            String authority,
            ContentProviderClient provider,
            SyncResult syncResult) {
        Intent intent = new Intent(getContext(), ForegroundSync.class);
        if (Build.VERSION.SDK_INT >= 26) {
            getContext().startForegroundService(intent);
        } else {
            getContext().startService(intent);
        }
    }

    /**
     * Set up the sync adapter
     */
    MediaSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }


    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    MediaSyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
    }
}
