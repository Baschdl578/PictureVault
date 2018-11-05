package de.smschindler.picturevault.sync;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import de.smschindler.picturevault.R;

import static de.smschindler.picturevault.sync.MediaSync.CHANNELID_SYNCONGOING;
import static de.smschindler.picturevault.sync.MediaSync.NOTIFICATION_ID;

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
                .setChannelId(CHANNELID_SYNCONGOING)
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
