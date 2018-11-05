package de.smschindler.picturevault.settings;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.MediaStore;

import de.smschindler.picturevault.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by baschdl on 21.08.17.
 */

public class SettingsManager {
    private static HashMap<String, SettingObject> allSettings;
    private static DBHelper helper;

    public static final String SETTING_SERVERADDRESS = "serveraddr";
    public static final String SETTING_SERVERPORT = "port";
    public static final String SETTING_USERNAME = "user";
    public static final String SETTING_PASS = "pass";
    public static final String SETTING_DOSYNC = "dosync";
    public static final String SETTING_WIFINAME = "wifiname";
    public static final String SETTING_WIFIRESTRICT = "wifirestrict";
    public static final String SETTING_WIFISPECIFICRESTRICT = "wifispecificrestrict";
    public static final String SETTING_STREAMVID = "streamvids";
    public static final String SETTING_PLAYVIDWHENREADY = "playwhenready";

    private static final String DB_BUCKETS_TABLE = "Buckets";
    private static final String DB_BUCKETS_COLUMN_NAME = "Name";
    private static final String DB_BUCKETS_COLUMN_DOSYNC = "DoSync";
    private static final String DB_SYNCIDS_TABLE = "SyncIds";
    private static final String DB_SYNCIDS_COLUMN_ID = "itemId";
    private static final String DB_SYNCIDS_COLUMN_RETRIES = "retries";
    private static final String DB_SYNCIDS_COLUMN_SIZE = "size";
    private static final String DB_SYNCIDS_COLUMN_TYPE = "mediatype";


    private static final String SQL_CREATE_BUCKETTABLE =
            "CREATE TABLE IF NOT EXISTS " + DB_BUCKETS_TABLE + " (" +
                    DB_BUCKETS_COLUMN_NAME + " VARCHAR(255) PRIMARY KEY, " +
                    DB_BUCKETS_COLUMN_DOSYNC + " INTEGER)";

    private static final String SQL_CREATE_FAILEDTABLE =
            "CREATE TABLE IF NOT EXISTS " + DB_SYNCIDS_TABLE + " (" +
                    DB_SYNCIDS_COLUMN_ID + " BIGINT PRIMARY KEY, " +
                    DB_SYNCIDS_COLUMN_SIZE + " BIGINT, " +
                    DB_SYNCIDS_COLUMN_TYPE + " int, " +
                    DB_SYNCIDS_COLUMN_RETRIES + " INTEGER)";

    private static final String SQL_DELETE_BUCKETTABLE =
            "DROP TABLE IF EXISTS " + DB_BUCKETS_TABLE;

    private static final String SQL_DELETE_FAILEDTABLE =
            "DROP TABLE IF EXISTS " + DB_SYNCIDS_TABLE;

    public static final Integer SYNCVAL_NOT_SET = -1;
    public static final Integer SYNCVAL_NO_SYNC = 0;
    public static final Integer SYNCVAL_DO_SYNC = 1;
    public static final Integer SYNCVAL_FIRST_TIME = 2;






    public static HashMap<String, SettingObject> getAllSettings(Context context) {
        if (allSettings == null) {
            allSettings = new HashMap<>();
            allSettings.put(SETTING_SERVERADDRESS, new TextSettingObject(context.getString(R.string.setting_name_serveraddr), context.getString(R.string.setting_text_serveraddr), SETTING_SERVERADDRESS, false, R.drawable.server, context.getString(R.string.setting_cat_connection), 0, 0));
            allSettings.put(SETTING_SERVERPORT, new TextSettingObject(context.getString(R.string.setting_name_serverport), context.getString(R.string.setting_text_serverport), SETTING_SERVERPORT, false, R.drawable.port, context.getString(R.string.setting_cat_connection), 1, 0));
            allSettings.put(SETTING_USERNAME, new TextSettingObject(context.getString(R.string.setting_name_username), context.getString(R.string.setting_text_username), SETTING_USERNAME, false, R.drawable.account, context.getString(R.string.setting_cat_login), 0, 1));
            allSettings.put(SETTING_PASS, new TextSettingObject(context.getString(R.string.setting_name_password), context.getString(R.string.setting_text_password), SETTING_PASS, true, R.drawable.pass, context.getString(R.string.setting_cat_login), 1, 1));
            allSettings.put(SETTING_WIFIRESTRICT, new BoolSettingObject(context.getString(R.string.setting_name_only_wifi), context.getString(R.string.setting_text_only_wifi), SETTING_WIFIRESTRICT, false, R.drawable.wifi, context.getString(R.string.setting_cat_sync), 1, 2));
            allSettings.put(SETTING_WIFISPECIFICRESTRICT, new BoolSettingObject(context.getString(R.string.setting_name_specific_wifi), context.getString(R.string.setting_text_specific_wifi), SETTING_WIFISPECIFICRESTRICT, false, R.drawable.wifi, context.getString(R.string.setting_cat_sync), 2, 2));
            allSettings.put(SETTING_WIFINAME, new TextSettingObject(context.getString(R.string.setting_name_wifi_name), context.getString(R.string.setting_text_wifi_name), SETTING_WIFINAME, false, R.drawable.wifi, SettingObject.ACTION_WIFINAME, context.getString(R.string.setting_cat_sync), 3, 2));
            allSettings.put(SETTING_DOSYNC, new BoolSettingObject(context.getString(R.string.setting_name_dosync), context.getString(R.string.setting_text_dosync), SETTING_DOSYNC, false, R.drawable.sync, SettingObject.ACTION_DOSYNC, context.getString(R.string.setting_cat_sync), 0, 2));
            allSettings.put(SETTING_STREAMVID, new BoolSettingObject(context.getString(R.string.setting_name_stream_vids), context.getString(R.string.setting_text_stream_vids), SETTING_STREAMVID, false, R.drawable.video_stream, context.getString(R.string.setting_cat_behavior), 0, 3));
            allSettings.put(SETTING_PLAYVIDWHENREADY, new BoolSettingObject(context.getString(R.string.setting_name_play_vids), context.getString(R.string.setting_text_play_vids), SETTING_PLAYVIDWHENREADY, false, R.drawable.play, context.getString(R.string.setting_cat_behavior), 1, 3));
        }
        return allSettings;
    }

    public static SettingObject[] getSettingArray(Context context) {
        ArrayList<SettingObject> outList = new ArrayList<>();
        for (String key : getAllSettings(context).keySet()) {
            outList.add(getAllSettings(context).get(key));
        }
        Collections.sort(outList, new Comparator<SettingObject>() {
            @Override
            public int compare(SettingObject settingObject, SettingObject t1) {
                int sort = settingObject.getCategorySort().compareTo(t1.getCategorySort());
                if (sort == 0) {
                    sort = settingObject.getSortId().compareTo(t1.getSortId());
                }
                return sort;
            }
        });
        SettingObject[] out = new SettingObject[outList.size()];
        outList.toArray(out);
        return out;
    }

    public static Boolean getBoolValue(Context context, String tag) {
        return ((BoolSettingObject) getAllSettings(context).get(tag)).getValue(context);
    }

    public static String getStringValue(Context context, String tag) {
        return ((TextSettingObject) getAllSettings(context).get(tag)).getValue(context);
    }

    public static void setValue(Context context, String tag, boolean newValue) {
        getAllSettings(context).get(tag).set(context, newValue);
    }

    public static void setValue(Context context, String tag, String newValue) {
        getAllSettings(context).get(tag).set(context, newValue);
    }

    public static int getIcon(Context context, String tag) {
        return getAllSettings(context).get(tag).getIconId();
    }

    public static String getName(Context context, String tag) {
        return getAllSettings(context).get(tag).getName();
    }

    public static String getText(Context context, String tag) {
        return getAllSettings(context).get(tag).getText();
    }


    private static DBHelper getDBHelper(Context context) {
        if (helper == null) {
            helper = new DBHelper(context);
        }
        return helper;
    }

    public static void addBucket(Context context, String bucket) {
        SQLiteDatabase db = getDBHelper(context).getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(DB_BUCKETS_COLUMN_NAME, bucket);
        values.put(DB_BUCKETS_COLUMN_DOSYNC, SYNCVAL_NOT_SET);

        long newRowId = db.insert(DB_BUCKETS_TABLE, null, values);
    }

    public static void setSyncBucket(Context context, String bucket, int syncval) {
        SQLiteDatabase db = getDBHelper(context).getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(DB_BUCKETS_COLUMN_DOSYNC, syncval);


        String selection = DB_BUCKETS_COLUMN_NAME + " = ?";
        String[] selectionArgs = { bucket };

        int count = db.update(
                DB_BUCKETS_TABLE,
                values,
                selection,
                selectionArgs);
    }

    public static String[] foundBuckets(Context context) {
        SQLiteDatabase db = getDBHelper(context).getReadableDatabase();

        String[] projection = {
                DB_BUCKETS_COLUMN_NAME
        };

        String selection = DB_BUCKETS_COLUMN_DOSYNC + " != ?";
        String[] args = new String[]{SYNCVAL_NOT_SET.toString()};

        Cursor cursor = db.query(
                DB_BUCKETS_TABLE,
                projection,
                selection,
                args,
                null,
                null,
                null
        );

        ArrayList<String> itemIds = new ArrayList<>();
        while(cursor.moveToNext()) {
            String name = cursor.getString(
                    cursor.getColumnIndexOrThrow(DB_BUCKETS_COLUMN_NAME));
            itemIds.add(name);
        }
        cursor.close();
        String[] out = new String[itemIds.size()];
        itemIds.toArray(out);
        return out;
    }

    public static void closeDB(Context context) {
        getDBHelper(context).close();
    }


    public static String[] bucketsByStatus(Context context, Integer status) {
        SQLiteDatabase db = getDBHelper(context).getReadableDatabase();

        String[] projection = {
                DB_BUCKETS_COLUMN_NAME
        };
        String selection = DB_BUCKETS_COLUMN_DOSYNC + " = ?";
        String[] selectionArgs = { status.toString() };

        Cursor cursor = db.query(
                DB_BUCKETS_TABLE,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        ArrayList<String> itemIds = new ArrayList<>();
        while(cursor.moveToNext()) {
            String name = cursor.getString(
                    cursor.getColumnIndexOrThrow(DB_BUCKETS_COLUMN_NAME));
            itemIds.add(name);
        }
        cursor.close();
        String[] out = new String[itemIds.size()];
        itemIds.toArray(out);
        return out;
    }


    public static String[] getSyncBuckets(Context context) {
        return bucketsByStatus(context, SYNCVAL_DO_SYNC);
    }


    public static String[] getFirstTimeBuckets(Context context) {
        return bucketsByStatus(context, SYNCVAL_FIRST_TIME);
    }

    public static void addSyncId(Context context, Long id, Long size, int type) {
        SQLiteDatabase db = getDBHelper(context).getReadableDatabase();

        String[] projection = {
                DB_SYNCIDS_COLUMN_ID
        };
        String selection = DB_SYNCIDS_COLUMN_ID + " = ?";
        String[] selectionArgs = { id.toString() };

        Cursor cursor = db.query(
                DB_SYNCIDS_TABLE,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        ArrayList<Long> itemIds = new ArrayList<>();
        while(cursor.moveToNext()) {
            Long name = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DB_SYNCIDS_COLUMN_ID));
            itemIds.add(name);
        }
        cursor.close();
        if (itemIds.contains(id)) {
            increaseFailedRetries(context, id);
            return;
        }

        db = getDBHelper(context).getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(DB_SYNCIDS_COLUMN_ID, id);
        values.put(DB_SYNCIDS_COLUMN_RETRIES, 0);
        values.put(DB_SYNCIDS_COLUMN_SIZE, size);
        values.put(DB_SYNCIDS_COLUMN_TYPE, type);


        long count = db.insert(
                DB_SYNCIDS_TABLE,
                null,
                values
                );
    }




    public static void removeSyncId(Context context, Long id) {
        SQLiteDatabase db = getDBHelper(context).getWritableDatabase();

        String selection = DB_SYNCIDS_COLUMN_ID + " = ?";
        String[] selectionArgs = { id.toString() };

        db.delete(DB_SYNCIDS_TABLE, selection, selectionArgs);
    }

    private static int failedRetries(Context context, Long id) {
        SQLiteDatabase db = getDBHelper(context).getReadableDatabase();

        String[] projection = {
                DB_SYNCIDS_COLUMN_RETRIES
        };
        String selection = DB_SYNCIDS_COLUMN_ID + " = ?";
        String[] args = new String[]{ id.toString() };


        Cursor cursor = db.query(
                DB_SYNCIDS_TABLE,
                projection,
                selection,
                args,
                null,
                null,
                null
        );

        int out = 0;
        while(cursor.moveToNext()) {
            out = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DB_SYNCIDS_COLUMN_RETRIES));
        }
        cursor.close();

        return out;
    }

    public static void increaseFailedRetries(Context context, Long id) {
        if (failedRetries(context, id) > 25) {
            removeSyncId(context, id);
            return;
        }


        SQLiteDatabase db = getDBHelper(context).getWritableDatabase();

        String selection = DB_SYNCIDS_COLUMN_ID + " = ?";
        String[] selectionArgs = { id.toString() };

        ContentValues values = new ContentValues();
        values.put(DB_SYNCIDS_COLUMN_RETRIES, DB_SYNCIDS_COLUMN_RETRIES + " + 1");


        int count = db.update(
                DB_SYNCIDS_TABLE,
                values,
                selection,
                selectionArgs);
    }

    private static long[] allSyncOfType(Context context, Integer type) {
        SQLiteDatabase db = getDBHelper(context).getReadableDatabase();

        String[] projection = {
                DB_SYNCIDS_COLUMN_ID
        };
        String selection = DB_SYNCIDS_COLUMN_TYPE + " = ?";
        String[] args = new String[]{ type.toString() };


        Cursor cursor = db.query(
                DB_SYNCIDS_TABLE,
                projection,
                selection,
                args,
                null,
                null,
                null
        );

        ArrayList<Long> itemIds = new ArrayList<>();
        while(cursor.moveToNext()) {
            long name = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DB_SYNCIDS_COLUMN_ID));
            itemIds.add(name);
        }
        cursor.close();
        long[] out = new long[itemIds.size()];
        Iterator<Long> iter = itemIds.iterator();
        int i = 0;
        while (iter.hasNext()) {
            out[i] = iter.next();
            i++;
        }
        return out;
    }

    public static long[] allSyncPics(Context context) {
        return allSyncOfType(context, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE);
    }

    public static long[] allSyncVids(Context context) {
        return allSyncOfType(context, MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO);
    }



    public static int syncItemCount(Context context) {
        SQLiteDatabase db = getDBHelper(context).getReadableDatabase();

        String[] projection = {
                DB_SYNCIDS_COLUMN_ID
        };
        Cursor cursor = db.query(
                DB_SYNCIDS_TABLE,
                projection,
                null,
                null,
                null,
                null,
                null
        );

        int out = cursor.getCount();
        cursor.close();
        return out;
    }

    public static long totalSyncSize(Context context) {
        SQLiteDatabase db = getDBHelper(context).getReadableDatabase();

        String[] projection = {
                "SUM( " + DB_SYNCIDS_COLUMN_SIZE + " ) AS `totalsize`"
        };
        Cursor cursor = db.query(
                DB_SYNCIDS_TABLE,
                projection,
                null,
                null,
                null,
                null,
                null
        );
        long out = 0;
        while(cursor.moveToNext()) {
            long size = cursor.getLong(
                    cursor.getColumnIndexOrThrow("totalsize"));
            out += size;
        }
        cursor.close();
        return out;

    }






    private static class DBHelper extends SQLiteOpenHelper {
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "PictureVault.db";

        public DBHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }


        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL(SQL_CREATE_BUCKETTABLE);
            sqLiteDatabase.execSQL(SQL_CREATE_FAILEDTABLE);

        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            //do nothing
        }

    }
}
