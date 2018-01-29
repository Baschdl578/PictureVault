package org.baschdl.picturevault.sync;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;

import org.baschdl.picturevault.R;
import org.baschdl.picturevault.Server;
import org.baschdl.picturevault.settings.SettingsManager;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.Context.CONNECTIVITY_SERVICE;

/**
 * Created by baschdl on 26.08.17.
 */

public class MediaSync {
    private String wifiname;
    private String ipAddress;
    private boolean doSync;
    private boolean onlyWifi;
    private boolean restrictWifi;

    static String ACTION_ADDLIBRARY = "PictureVault.MediaSyncAdapter.addlib";
    static String EXTRA_BUCKETNAME = "bucketname";
    static String EXTRA_NOTIFICATIONID = "notificationid";
    static String EXTRA_DOSYNCLIB = "dosynclib";
    static int NOTIFICATION_ID = 128285;
    static String NEWBUCKETTAG = "newbucket";
    private static String LOGTAG = MediaSyncAdapter.class.getName();
    public static final String CHANNELID_NEWBUCKET = "channelnewbucket";
    public static final String CHANNELID_SYNCDONE = "channelsyncdone";
    public static final String CHANNELID_SYNCONGOING = "channelsyncongoing";


    private static MediaSync instance;
    private boolean running;
    private boolean canceled;

    /**
     * Set up the sync adapter
     */
    private MediaSync(Context context) {
        wifiname = SettingsManager.getStringValue(context, SettingsManager.SETTING_WIFINAME);
        ipAddress = SettingsManager.getStringValue(context, SettingsManager.SETTING_SERVERADDRESS);
        doSync = SettingsManager.getBoolValue(context, SettingsManager.SETTING_DOSYNC);
        onlyWifi = SettingsManager.getBoolValue(context, SettingsManager.SETTING_WIFIRESTRICT);
        restrictWifi = SettingsManager.getBoolValue(context, SettingsManager.SETTING_WIFISPECIFICRESTRICT);
        canceled = false;
        running = false;
    }

    public static synchronized MediaSync getInstance(Context context) {
        if (instance == null) instance = new MediaSync(context);
        return instance;
    }

    synchronized boolean isRunning() {
        return running;
    }

    private synchronized boolean checkAndSetRunning() {
        if (isRunning()) return false;
        running = true;
        return true;
    }

    synchronized void cancel() {
        canceled = true;
    }

    public synchronized boolean isCanceled() {
        return canceled;
    }

    private synchronized void stop() {
        canceled = false;
        running = false;
    }


    public void start(Context context) {
        if (!doSync) {
            exit(context);
            return;
        }
        if (!checkAndSetRunning()) {
            exit(context);
            return;
        }
        if (!connected(context)) {
            exit(context);
            return;
        }
        sync(context);
    }

    /**
     * Checks if the device is connected to the server and the right WIFI network (if applicable)
     *
     * @return True if connected
     */
    private boolean connected(Context context) {
        boolean wifi = true;
        if (onlyWifi) {
            WifiManager mWifiMgr = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            wifi = wifiInfo.isConnected();
            if (restrictWifi && wifi) {
                String wifiName = mWifiMgr.getConnectionInfo().getSSID();
                wifi = wifiName.equals(wifiname);
            }
        }
        if (wifi) {
            try {
                return InetAddress.getByName(ipAddress).isReachable(10 * 1000) && Server.checkPulse(context);
            } catch (UnknownHostException e) {
                return false;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    private void sync(Context context) {
        long start = System.currentTimeMillis();
        ContentResolver mContentResolver = context.getContentResolver();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(context, CancelBroadcastReceiver.class);
        PendingIntent cancelIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.synching))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.close, context.getString(R.string.cancel), cancelIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setChannelId(CHANNELID_SYNCONGOING)
                .setProgress(0, 0, true);

        Notification notification = builder.build();
        manager.notify(NOTIFICATION_ID, notification);

        Long lastSync = Server.getLastSync(context);
        findSyncItems(context, mContentResolver, lastSync);
        checkForNewBuckets(context, mContentResolver);

        int totalItems = SettingsManager.syncItemCount(context);

        if (totalItems <= 0) {
            exit(context);
            return;
        }

        long totalSize = SettingsManager.totalSyncSize(context);
        long currentSize = 0L;
        int uploadedVids = 0;
        int uploadedPics = 0;

        if (!updateNotification(context, totalItems, totalSize, 0, 0, cancelIntent)) {
            return;
        }

        long size;
        for (long picId: SettingsManager.allSyncPics(context)) {
            if (isCanceled()) {
                exit(context);
                return;
            }
            size = uploadPic(context, mContentResolver, picId, totalSize, currentSize, totalItems, uploadedPics + uploadedVids, cancelIntent);
            if (size > 0) {
                SettingsManager.removeSyncId(context, picId);
                uploadedPics++;
                currentSize += size;
                if (!updateNotification(context, totalItems, totalSize, uploadedPics + uploadedVids, currentSize, cancelIntent)) {
                    return;
                }
            } else {
                SettingsManager.increaseFailedRetries(context, picId);
            }
        }

        for (long vidId: SettingsManager.allSyncVids(context)) {
            if (isCanceled()) {
                exit(context);
                return;
            }
            size = uploadVid(context, mContentResolver, vidId, totalSize, currentSize, totalItems, uploadedPics + uploadedVids, cancelIntent);
            if (size > 0) {
                SettingsManager.removeSyncId(context, vidId);
                uploadedVids++;
                currentSize += size;
                if (!updateNotification(context, totalItems, totalSize, uploadedPics + uploadedVids, currentSize, cancelIntent)) {
                    return;
                }
            } else {
                SettingsManager.increaseFailedRetries(context, vidId);
            }
        }

        if (uploadedPics + uploadedVids > 0) {
            Server.setLastSync(context, start);
            String text = String.format(Locale.ENGLISH, context.getString(R.string.synchdone), Long.toString(uploadedPics), Long.toString(uploadedVids));

            NotificationCompat.Builder builder2 = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(context.getString(R.string.synching))
                    .setContentText(text.split("\n")[0])
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setChannelId(CHANNELID_SYNCDONE)
                    .setOngoing(false);
            NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
            style.setBigContentTitle(context.getString(R.string.synching));
            style.bigText(text);
            builder2.setStyle(style);

            Notification notification2 = builder2.build();
            manager.notify(NOTIFICATION_ID + 1, notification2);
        }
        stop();
    }

    /**
     * This will search and count all pictures and videos that need to be uploaded and get their total size
     *
     * @param lastSync Timestamp of the last synchronisation
     */
    private void findSyncItems(Context context, ContentResolver mContentResolver , Long lastSync) {
        Uri files = MediaStore.Files.getContentUri("external");

        String[] projection = new String[]{
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.SIZE,
                MediaStore.Files.FileColumns.MEDIA_TYPE
        };

        String selection = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + " = ? OR "
                + MediaStore.Files.FileColumns.MEDIA_TYPE + " = ? ) AND "
                + MediaStore.MediaColumns.DATE_ADDED + " >= ? AND "
                + MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " = ?";

        String[] selectionArgs = new String[4];
        selectionArgs[0] = Integer.toString(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE);
        selectionArgs[1] = Integer.toString(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO);
        selectionArgs[2] = Long.valueOf(lastSync / 1000).toString();


        String[] buckets = SettingsManager.getSyncBuckets(context);

        for (String bucket : buckets) {
            selectionArgs[3] = bucket;
            Cursor cur = mContentResolver.query(files,
                    projection, // Which columns to return
                    selection,       // Which rows to return (all rows)
                    selectionArgs,       // Selection arguments (none)
                    null        // Ordering
            );

            assert cur != null;
            if (cur.moveToFirst()) {
                Long size;
                Long id;
                Integer type;

                int sizeColumn = cur.getColumnIndex(
                        MediaStore.MediaColumns.SIZE
                );

                int typeColumn = cur.getColumnIndex(
                        MediaStore.Files.FileColumns.MEDIA_TYPE
                );

                int idColumn = cur.getColumnIndex(
                        MediaStore.MediaColumns._ID
                );

                do {
                    // Get the field values
                    size = cur.getLong(sizeColumn);
                    id = cur.getLong(idColumn);
                    type = cur.getInt(typeColumn);

                    SettingsManager.addSyncId(context, id, size, type);
                } while (cur.moveToNext());
                if (!cur.isClosed()) {
                    cur.close();
                }
            }
        }


        selection = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + " = ? OR "
                + MediaStore.Files.FileColumns.MEDIA_TYPE + " = ? ) AND "
                + MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " = ?";

        selectionArgs = new String[3];
        selectionArgs[0] = Integer.toString(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE);
        selectionArgs[1] = Integer.toString(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO);


        buckets = SettingsManager.getFirstTimeBuckets(context);

        for (String bucket : buckets) {
            selectionArgs[2] = bucket;
            Cursor cur = mContentResolver.query(files,
                    projection, // Which columns to return
                    selection,       // Which rows to return (all rows)
                    selectionArgs,       // Selection arguments (none)
                    null        // Ordering
            );

            assert cur != null;
            if (cur.moveToFirst()) {
                Long size;
                Long id;
                Integer type;

                int sizeColumn = cur.getColumnIndex(
                        MediaStore.MediaColumns.SIZE
                );

                int typeColumn = cur.getColumnIndex(
                        MediaStore.Files.FileColumns.MEDIA_TYPE
                );

                int idColumn = cur.getColumnIndex(
                        MediaStore.MediaColumns._ID
                );

                do {
                    // Get the field values
                    size = cur.getLong(sizeColumn);
                    id = cur.getLong(idColumn);
                    type = cur.getInt(typeColumn);

                    SettingsManager.addSyncId(context, id, size, type);
                } while (cur.moveToNext());
                if (!cur.isClosed()) {
                    cur.close();
                }
            }
            SettingsManager.setSyncBucket(context, bucket, SettingsManager.SYNCVAL_DO_SYNC);
        }

    }

    /**
     * Searches for new buckets
     *
     * @param context Context
     */
    private void checkForNewBuckets(Context context, ContentResolver mContentResolver) {
        Uri files = MediaStore.Files.getContentUri("external");

        String selection = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + " = "
                + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                + " OR "
                + MediaStore.Files.FileColumns.MEDIA_TYPE + " = "
                + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + " )";

        String[] buckets = SettingsManager.foundBuckets(context);
        for (String currentBucket : buckets) {
            selection += " AND " + MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " != \"" + currentBucket + "\"";
        }

        String projection[] = {"Distinct " + MediaStore.Images.Media.BUCKET_DISPLAY_NAME};

        Cursor cur = mContentResolver.query(files,
                projection, // Which columns to return
                selection,       // Which rows to return (all rows)
                null,       // Selection arguments (none)
                null        // Ordering
        );

        assert cur != null;
        if (cur.moveToFirst()) {
            String bucket;

            int bucketColumn = cur.getColumnIndex(
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            );

            do {
                // Get the field values
                bucket = cur.getString(bucketColumn);

                foundBucket(context, bucket);
            } while (cur.moveToNext());
            if (!cur.isClosed()) {
                cur.close();
            }
        }
    }

    private void foundBucket(Context context, String bucketName) {
        SettingsManager.addBucket(context, bucketName);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int id = this.getClass().getName().hashCode() + bucketName.hashCode();

        Intent intent = new Intent(ACTION_ADDLIBRARY);
        intent.putExtra(EXTRA_BUCKETNAME, bucketName);
        intent.putExtra(EXTRA_NOTIFICATIONID, id);
        intent.putExtra(EXTRA_DOSYNCLIB, true);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, Long.valueOf(System.currentTimeMillis()).intValue(), intent, 0);

        Intent intent2 = new Intent(ACTION_ADDLIBRARY);
        intent2.putExtra(EXTRA_BUCKETNAME, bucketName);
        intent2.putExtra(EXTRA_NOTIFICATIONID, id);
        intent2.putExtra(EXTRA_DOSYNCLIB, false);
        PendingIntent pendingIntent2 = PendingIntent.getBroadcast(context, Long.valueOf(System.currentTimeMillis()).intValue(), intent2, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.bucketfoundtitle))
                .setContentText(String.format(context.getString(R.string.bucketfound), bucketName).split("\n")[0])
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setChannelId(CHANNELID_NEWBUCKET)
                .addAction(R.drawable.cloud_upload, context.getString(R.string.dosync), pendingIntent)
                .addAction(R.drawable.close, context.getString(R.string.notsync), pendingIntent2)
                .setOngoing(true);
        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
        style.setBigContentTitle(context.getString(R.string.bucketfoundtitle));
        style.bigText(String.format(context.getString(R.string.bucketfound), bucketName));
        builder.setStyle(style);

        Notification notification = builder.build();
        manager.notify(NEWBUCKETTAG, id, notification);

    }

    public boolean updateNotification(Context context, int totalItems, long totalSize, int currentItem, long currentSize, PendingIntent cancelIntent) {
        if (isCanceled()) {
            exit(context);
            return false;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        String doneCountPad = Long.toString(currentItem);
        String tmp = Long.toString(totalItems);
        while (tmp.length() > doneCountPad.length()) {
            doneCountPad = "0" + doneCountPad;
        }
        String message = context.getString(R.string.synching);
        int done = Double.valueOf(currentSize * (Integer.MAX_VALUE / totalSize)).intValue();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(message + "\t(" + doneCountPad + "/" + tmp + ")")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setChannelId(CHANNELID_SYNCONGOING)
                .addAction(R.drawable.close, context.getString(R.string.cancel), cancelIntent)
                .setDeleteIntent(cancelIntent)
                .setProgress(Integer.MAX_VALUE, done, false);

        Notification notification = builder.build();
        manager.notify(NOTIFICATION_ID, notification);
        return true;
    }

    private long uploadPic(Context context, ContentResolver mContentResolver, long id, long totalSize, long startSize, int totalItems, int currentItem, PendingIntent cancelIntent) {
        String selection = MediaStore.Images.Media._ID + " = ?";
        String[] args = new String[]{Long.toString(id)};
        long outSize = -1L;
        Long outId = null;

        String[] projection = new String[]{
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Images.ImageColumns.SIZE,
                MediaStore.Images.ImageColumns.DATE_TAKEN,
                MediaStore.Images.ImageColumns.DATE_MODIFIED,
                MediaStore.Images.ImageColumns.DATA,
                MediaStore.Images.ImageColumns.DATE_ADDED,
                MediaStore.Images.ImageColumns.LATITUDE,
                MediaStore.Images.ImageColumns.LONGITUDE,
                MediaStore.Images.ImageColumns.DATE_ADDED
        };

        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        Cursor cur = mContentResolver.query(uri,
                projection, // Which columns to return
                selection,       // Which rows to return (all rows)
                args,       // Selection arguments (none)
                null        // Ordering
        );

        if (cur.moveToFirst()) {
            Long size;
            String path;
            String bucket;
            Long date_taken;
            Double latitude;
            Double longitude;
            Long modified;
            Long added_date;

            do {
                if (isCanceled()) {
                    cur.close();
                    return -1L;
                }
                size = cur.getLong(cur.getColumnIndex(
                        MediaStore.Images.ImageColumns.SIZE
                ));
                modified = cur.getLong(cur.getColumnIndex(
                        MediaStore.Images.ImageColumns.DATE_MODIFIED
                ));
                date_taken = cur.getLong(cur.getColumnIndex(
                        MediaStore.Images.ImageColumns.DATE_TAKEN
                ));
                path = cur.getString(cur.getColumnIndex(
                        MediaStore.Images.ImageColumns.DATA
                ));
                bucket = cur.getString(cur.getColumnIndex(
                        MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
                ));
                latitude = cur.getDouble(cur.getColumnIndex(
                        MediaStore.Images.ImageColumns.LATITUDE
                ));
                longitude = cur.getDouble(cur.getColumnIndex(
                        MediaStore.Images.ImageColumns.LONGITUDE
                ));
                added_date = cur.getLong(cur.getColumnIndex(
                        MediaStore.Images.ImageColumns.DATE_ADDED
                ));

                Long[] res = getRes(path);

                outId = upload(context, path, bucket, date_taken, added_date, modified, longitude, latitude, res[0], res[1], -1L, size, totalSize, startSize, totalItems, currentItem, cancelIntent);

                if (outId != null && outId > 0) {
                    outSize = size;
                }

            } while (cur.moveToNext());

            if (!cur.isClosed()) {
                cur.close();
            }

        }
        return outSize;

    }

    private long uploadVid(Context context, ContentResolver mContentResolver, long id, long totalSize, long startSize, int totalItems, int currentItem, PendingIntent cancelIntent) {
        String selection = MediaStore.Images.Media._ID + " = ?";
        String[] args = new String[]{Long.toString(id)};
        Long outId = null;
        long outSize = -1L;

        String[] projection = new String[]{
                MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Video.VideoColumns.SIZE,
                MediaStore.Video.VideoColumns.DATE_TAKEN,
                MediaStore.Video.VideoColumns.DATE_MODIFIED,
                MediaStore.Video.VideoColumns.DATA,
                MediaStore.Video.VideoColumns._ID,
                MediaStore.Video.VideoColumns.LATITUDE,
                MediaStore.Video.VideoColumns.LONGITUDE,
                MediaStore.Video.VideoColumns.DURATION,
                MediaStore.Video.VideoColumns.RESOLUTION,
                MediaStore.Video.VideoColumns.DATE_ADDED
        };

        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        Cursor cur = mContentResolver.query(uri,
                projection, // Which columns to return
                selection,       // Which rows to return (all rows)
                args,       // Selection arguments (none)
                null        // Ordering
        );

        if (cur.moveToFirst()) {
            Long size;
            String path;
            Long date_taken;
            Double latitude;
            Double longitude;
            Long modified;
            Long duration;
            String resolution;
            String bucket;
            Long date_added;

            do {
                if (isCanceled()) {
                    cur.close();
                    return outSize;
                }
                size = cur.getLong(cur.getColumnIndex(
                        MediaStore.Video.VideoColumns.SIZE
                ));
                modified = cur.getLong(cur.getColumnIndex(
                        MediaStore.Video.VideoColumns.DATE_MODIFIED
                ));
                date_taken = cur.getLong(cur.getColumnIndex(
                        MediaStore.Video.VideoColumns.DATE_TAKEN
                ));
                path = cur.getString(cur.getColumnIndex(
                        MediaStore.Video.VideoColumns.DATA
                ));
                latitude = cur.getDouble(cur.getColumnIndex(
                        MediaStore.Video.VideoColumns.LATITUDE
                ));
                longitude = cur.getDouble(cur.getColumnIndex(
                        MediaStore.Video.VideoColumns.LONGITUDE
                ));
                duration = cur.getLong(cur.getColumnIndex(
                        MediaStore.Video.VideoColumns.DURATION
                ));
                resolution = cur.getString(cur.getColumnIndex(
                        MediaStore.Video.VideoColumns.RESOLUTION
                ));
                bucket = cur.getString(cur.getColumnIndex(
                        MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
                ));
                date_added = cur.getLong(cur.getColumnIndex(
                        MediaStore.Images.ImageColumns.DATE_ADDED
                ));

                String[] res = resolution.split("x");

                outId = upload(context, path, bucket, date_taken, date_added, modified, longitude, latitude, Long.parseLong(res[0]), Long.parseLong(res[1]), duration, size, totalSize, startSize, totalItems, currentItem, cancelIntent);

                if (outId != null && outId > 0) {
                    outSize = size;
                }

            } while (cur.moveToNext());

            if (!cur.isClosed()) {
                cur.close();
            }

        }

        return outSize;
    }

    private Long[] getRes(String path) {
        BitmapFactory.Options bitMapOption=new BitmapFactory.Options();
        bitMapOption.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(path, bitMapOption);
        int width=bitMapOption.outWidth;
        int height=bitMapOption.outHeight;

        return new Long[]{(long) width, (long) height};
    }

    /**
     * Get a date form the filename
     *
     * @param fileName Filename
     * @return Timestamp of the date
     */
    private Long extractDateFromName(String fileName) {
        String name = fileName;
        Pattern regex = Pattern.compile("\\D\\d\\d\\d\\d\\d\\d\\d\\d\\D");
        Matcher matcher = regex.matcher(name);

        while (matcher.find()) {
            name = matcher.group();
            name = name.substring(1, name.length() - 1);
            int year = Integer.parseInt(name.substring(0, 4));
            int month = Integer.parseInt(name.substring(4, 6));
            int day = Integer.parseInt(name.substring(6, 8));
            if (year < 1990 || year > Calendar.getInstance().get(Calendar.YEAR)) {
                continue;
            }
            if (month < 1 || month > 12) {
                continue;
            }
            if (day < 1 || day > 31) {
                continue;
            }

            Calendar cal = Calendar.getInstance();
            cal.set(year, month - 1, day, 0, 0, 0);
            return cal.getTimeInMillis();

        }

        regex = Pattern.compile("^\\d\\d\\d\\d\\d\\d\\d\\d\\D");
        matcher = regex.matcher(name);

        while (matcher.find()) {
            name = matcher.group();
            name = name.substring(0, name.length() - 1);
            int year = Integer.parseInt(name.substring(0, 4));
            int month = Integer.parseInt(name.substring(4, 6));
            int day = Integer.parseInt(name.substring(6, 8));
            if (year < 1990 || year > Calendar.getInstance().get(Calendar.YEAR)) {
                continue;
            }
            if (month < 1 || month > 12) {
                continue;
            }
            if (day < 1 || day > 31) {
                continue;
            }

            Calendar cal = Calendar.getInstance();
            cal.set(year, month - 1, day, 0, 0, 0);
            return cal.getTimeInMillis();

        }

        regex = Pattern.compile("\\D\\d\\d\\d\\d\\d\\d\\d\\d$");
        matcher = regex.matcher(name);
        while (matcher.find()) {
            name = matcher.group();
            name = name.substring(0, name.length() - 1);
            int year = Integer.parseInt(name.substring(0, 4));
            int month = Integer.parseInt(name.substring(4, 6));
            int day = Integer.parseInt(name.substring(6, 8));
            if (year < 1990 || year > Calendar.getInstance().get(Calendar.YEAR)) {
                continue;
            }
            if (month < 1 || month > 12) {
                continue;
            }
            if (day < 1 || day > 31) {
                continue;
            }

            Calendar cal = Calendar.getInstance();
            cal.set(year, month - 1, day, 0, 0, 0);
            return cal.getTimeInMillis();

        }

        return null;
    }

    private Long upload(Context context, String path, String bucket, Long date_taken, Long date_added, Long modifiedArg, Double longitudeArg, Double latitudeArg, Long h_res, Long v_res, Long duration, Long size, long totalSize, long startSize, int totalItems, int currentItem, PendingIntent cancelIntent) {
        File f = new File(path);
        Long fromFile = extractDateFromName(f.getName());
        Long modified = System.currentTimeMillis();
        if (modifiedArg != null) modified = modifiedArg;
        if (modified < 100000000000L) modified *= 1000L;


        Long created = modified;
        if (fromFile != null && date_added != null && date_taken != null) {
            if (fromFile < date_taken && fromFile + 24 * 3600 * 1000 > date_taken) {
                created = date_taken;
            } else {
                if (fromFile < date_added && fromFile + 24 * 3600 * 1000 > date_added) {
                    created = date_added;
                } else {
                    created = fromFile;
                }
            }
        } else {
            if (fromFile != null && date_taken != null) {
                if (fromFile < date_taken && fromFile + 24 * 3600 * 1000 > date_taken) {
                    created = date_taken;
                } else {
                    created = fromFile;
                }
            } else {
                if (fromFile != null && date_added != null) {
                    if (fromFile < date_added && fromFile + 24 * 3600 * 1000 > date_added) {
                        created = date_added;
                    } else {
                        created = fromFile;
                    }
                } else {
                    if (date_added != null && date_taken != null) {
                        created = Math.min(date_added, date_taken);
                    } else {
                        if (date_taken != null) {
                            created = date_taken;
                        } else {
                            created = date_added;
                        }
                    }
                }
            }
        }

        if (created != null && created < 100000000000L) created *= 1000L;
        Double latitude = 0.0;
        Double longitude = 0.0;
        if (latitudeArg != null) latitude = latitudeArg;
        if (longitudeArg != null) longitude = longitudeArg;

        return Server.uploadFile(context, f, bucket, created, modified, latitude, longitude, h_res, v_res, duration, size, totalSize, startSize, totalItems, currentItem, cancelIntent);
    }

    private synchronized void exit(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);
        SettingsManager.closeDB(context);
        stop();
        instance = null;
    }
}
