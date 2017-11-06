package org.baschdl.picturevault;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.baschdl.picturevault.sync.MediaSync;

import static android.content.Context.ACCOUNT_SERVICE;

/**
 * Utilities to be used anywhere in the application
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class Utilities {
    private static final long SYNC_INTERVAL = 3600L;
    // The authority for the sync adapter's content provider
    private static final String AUTHORITY = "org.baschdl.picturevault.provider";
    // An account type, in the form of a domain name
    private static final String ACCOUNT_TYPE = "org.baschdl.picturevault";
    // The account name
    private static final String ACCOUNT = "Media Synchronization";


    /**
     * Creates a sync account if it does not exist.
     *
     * @param context Context to get an account manager
     * @return The new account or null if it exists
     */
    private static Account createSyncAccount(Context context) {
        AccountManager accountManager =
                (AccountManager) context.getSystemService(
                        ACCOUNT_SERVICE);

        if (accountManager.getAccountsByType(ACCOUNT_TYPE).length > 0) {
            return null;
        }

        Account newAccount = new Account(
                ACCOUNT, ACCOUNT_TYPE);

        if (accountManager.addAccountExplicitly(newAccount, null, null)) {
            Log.i("Utilities", "Successfully added account");
        } else {
            Log.i("Chooser", "Error creating Account");
        }
        return newAccount;
    }

    /**
     * Sets up the sync account for the first time
     *
     * @param context Context to get the account manager
     */
    public static void setupSync(Context context) {
        Account account = createSyncAccount(context);
        if (account == null) return;
        if (ContentResolver.getPeriodicSyncs(account, AUTHORITY).size() <= 0) {
            ContentResolver.addPeriodicSync(
                    account,
                    AUTHORITY,
                    Bundle.EMPTY,
                    SYNC_INTERVAL);
        }
        ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
    }

    /**
     * Activate sync account
     *
     * @param context To get the account manager
     */
    public static void activateSync(Context context) {
        setupSync(context);

        AccountManager accountManager =
                (AccountManager) context.getSystemService(
                        ACCOUNT_SERVICE);

        for (Account account: accountManager.getAccountsByType(ACCOUNT_TYPE)) {
            ContentResolver.removePeriodicSync(account, AUTHORITY, Bundle.EMPTY);
            ContentResolver.addPeriodicSync(
                    account,
                    AUTHORITY,
                    Bundle.EMPTY,
                    SYNC_INTERVAL);
            ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
        }
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int importance = NotificationManager.IMPORTANCE_LOW;

        String id = MediaSync.CHANNELID_NEWBUCKET;
        CharSequence name = context.getString(R.string.channel_newbucket_name);
        String description = context.getString(R.string.channel_newbucket_description);

        NotificationChannel mChannel = new NotificationChannel(id, name, importance);

        mChannel.setDescription(description);
        mNotificationManager.createNotificationChannel(mChannel);

        id = MediaSync.CHANNELID_SYNCONGOING;
        name = context.getString(R.string.channel_syncongoing_name);
        description = context.getString(R.string.channel_syncongoing_description);

        mChannel = new NotificationChannel(id, name, importance);

        mChannel.setDescription(description);
        mNotificationManager.createNotificationChannel(mChannel);

        id = MediaSync.CHANNELID_SYNCDONE;
        name = context.getString(R.string.channel_syncdone_name);
        description = context.getString(R.string.channel_syncdone_description);

        mChannel = new NotificationChannel(id, name, importance);

        mChannel.setDescription(description);
        mNotificationManager.createNotificationChannel(mChannel);



    }
}
