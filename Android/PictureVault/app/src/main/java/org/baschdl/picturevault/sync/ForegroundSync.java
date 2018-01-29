package org.baschdl.picturevault.sync;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import org.baschdl.picturevault.R;

import static org.baschdl.picturevault.sync.MediaSync.NOTIFICATION_ID;

/**
 * Foreground Sync Service
 *
 * @author Sebastian Schindler
 * @version 1.0
 */

public class ForegroundSync extends IntentService {

    public ForegroundSync() {
        super("ForegroundPictureSync");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Intent intent2 = new Intent(getApplicationContext(), CancelBroadcastReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent2, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getApplicationContext().getString(R.string.synching))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.close, getApplicationContext().getString(R.string.cancel), pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, true);

        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);
        MediaSync.getInstance(getApplicationContext()).start(getApplicationContext());
    }
}
