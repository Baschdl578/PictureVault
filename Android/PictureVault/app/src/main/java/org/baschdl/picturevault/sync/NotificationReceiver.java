package org.baschdl.picturevault.sync;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import org.baschdl.picturevault.settings.SettingsManager;


/**
 * Receiver for Notification braodcasts.
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String bucketName = intent.getStringExtra(MediaSync.EXTRA_BUCKETNAME);
        int notificationId = intent.getIntExtra(MediaSync.EXTRA_NOTIFICATIONID, -1);
        boolean doSync = intent.getBooleanExtra(MediaSync.EXTRA_DOSYNCLIB, false);
        int syncVal = SettingsManager.SYNCVAL_FIRST_TIME;
        if (!doSync) syncVal = SettingsManager.SYNCVAL_NO_SYNC;

        SettingsManager.setSyncBucket(context, bucketName, syncVal);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(MediaSync.NEWBUCKETTAG, notificationId);

        if (MediaSync.getInstance(context) == null || !MediaSync.getInstance(context).isRunning()) {
            SettingsManager.closeDB(context);
        }

        if (Build.VERSION.SDK_INT > 22)
            checkAndStart(context);
    }

    @TargetApi(23)
    private void checkAndStart(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        StatusBarNotification[] notifications = manager.getActiveNotifications();

        boolean others = false;
        for (StatusBarNotification notification : notifications) {
            if (MediaSync.NEWBUCKETTAG.equals(notification.getTag())) others = true;
        }
        if (!others) {
            Intent intent = new Intent(context, ForegroundSync.class);
            context.startService(intent);
        }
    }
}
