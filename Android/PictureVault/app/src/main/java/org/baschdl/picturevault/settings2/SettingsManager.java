package org.baschdl.picturevault.settings2;

import android.animation.Animator;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.FrameLayout;

import com.dx.dxloadingbutton.lib.LoadingButton;
import com.google.common.hash.Hashing;

import org.baschdl.picturevault.R;
import org.baschdl.picturevault.loaders.PulseCheck;
import org.baschdl.picturevault.loaders.ServerCheck;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

/**
 * Created by baschdl on 21.08.17.
 */

public class SettingsManager {
    private static HashMap<Integer, SettingCategory> allCategories;
    private static DBHelper helper;

    public static final SettingID SETTING_SERVERADDRESS = new SettingID(R.string.setting_cat_connection, "serveraddr");
    public static final SettingID SETTING_SERVERPORT = new SettingID(R.string.setting_cat_connection, "port");
    public static final SettingID SETTING_USERNAME = new SettingID(R.string.setting_cat_login, "user");
    public static final SettingID SETTING_PASS = new SettingID(R.string.setting_cat_login, "pass");
    public static final SettingID SETTING_DOSYNC = new SettingID(R.string.setting_cat_sync, "dosync");
    public static final SettingID SETTING_WIFINAME = new SettingID(R.string.setting_cat_sync, "wifiname");
    public static final SettingID SETTING_WIFIRESTRICT = new SettingID(R.string.setting_cat_sync, "wifirestrict");
    public static final SettingID SETTING_WIFISPECIFICRESTRICT = new SettingID(R.string.setting_cat_sync, "wifispecificrestrict");
    public static final SettingID SETTING_STREAMVID = new SettingID(R.string.setting_cat_behavior, "streamvids");
    public static final SettingID SETTING_PLAYVIDWHENREADY = new SettingID(R.string.setting_cat_behavior, "playwhenready");

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






    public static HashMap<Integer, SettingCategory> getAllSettings(final Context context) {
        if (allCategories == null) {
            allCategories = new HashMap<>();
            TextSetting ip = new TextSetting(R.string.setting_name_serveraddr, R.string.setting_text_serveraddr, R.drawable.server, SETTING_SERVERADDRESS.tag, 0);
            TextSetting port = new TextSetting(R.string.setting_name_serverport, R.string.setting_text_serverport, R.drawable.server, SETTING_SERVERPORT.tag, 1);
            SettingCategory connection = new SettingCategory(R.string.setting_cat_connection, 0, ip, port) {
                LoadingButton loadingButton = view.findViewById(R.id.loaing);
                FrameLayout loadingFrame = view.findViewById(R.id.loadingFrame);
                @Override
                public void verify(Context context1) {
                    LoadingButton loadingButton = view.findViewById(R.id.loaing);
                    final FrameLayout loadingFrame = view.findViewById(R.id.loadingFrame);
                    boolean set = true;
                    for (AbstractSetting setting: this.allSettings()) {
                        if (!setting.isSet(context1)) {
                            set = false;
                            break;
                        }
                    }
                    if (!set) return;
                    loadingFrame.setAlpha(0F);
                    loadingFrame.setVisibility(View.VISIBLE);
                    loadingButton.startLoading();
                    loadingFrame.animate().setDuration(200).alpha(1F).start();
                    new MyServerCheck().execute(getSetting(SETTING_SERVERADDRESS.tag) + ":" + getSetting(SETTING_SERVERPORT.tag));
                }
                class MyServerCheck extends ServerCheck {
                    @Override
                    protected void onPostExecute(Boolean verified) {
                        super.onPostExecute(verified);
                        if (verified) {
                            loadingButton.loadingSuccessful();
                        } else {
                            loadingButton.loadingFailed();
                            view.findViewById(R.id.failedText).setVisibility(View.VISIBLE);
                        }
                        loadingFrame.animate().alpha(0F).setDuration(200).setStartDelay(1000).setListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animator) {}
                            @Override
                            public void onAnimationEnd(Animator animator) {
                                loadingFrame.setVisibility(View.GONE);
                                view.findViewById(R.id.failedText).setVisibility(View.GONE);
                            }
                            @Override
                            public void onAnimationCancel(Animator animator) {}
                            @Override
                            public void onAnimationRepeat(Animator animator) {}
                        }).start();
                    }
                }
            };



            TextSetting email = new TextSetting(R.string.setting_name_username, R.string.setting_text_username, R.drawable.server, SETTING_USERNAME.tag, 0);
            TextSetting pass = new TextSetting(R.string.setting_name_password, R.string.setting_text_password, R.drawable.server, SETTING_PASS.tag, 1) {
                @Override
                public boolean set(Context context1, String newValue) {
                    String hash = Hashing.sha512().hashString(newValue, Charset.defaultCharset()).toString();
                    return super.set(context1, hash);
                }
            };
            SettingCategory login = new SettingCategory(R.string.setting_cat_connection, 1, email, pass) {
                LoadingButton loadingButton = view.findViewById(R.id.loaing);
                FrameLayout loadingFrame = view.findViewById(R.id.loadingFrame);
                @Override
                public void verify(Context context1) {
                    boolean set = true;
                    for (AbstractSetting setting: this.allSettings()) {
                        if (!setting.isSet(context1)) {
                            set = false;
                            break;
                        }
                    }
                    if (!set) return;
                    loadingFrame.setAlpha(0F);
                    loadingFrame.setVisibility(View.VISIBLE);
                    loadingButton.startLoading();
                    loadingFrame.animate().setDuration(200).alpha(1F).start();
                    new MyPulse().execute();
                }

                class MyPulse extends PulseCheck {
                    @Override
                    protected void onPostExecute(Boolean verified) {
                        super.onPostExecute(verified);
                        if (verified) {
                            loadingButton.loadingSuccessful();
                        } else {
                            loadingButton.loadingFailed();
                            view.findViewById(R.id.failedText).setVisibility(View.VISIBLE);
                        }
                        loadingFrame.animate().alpha(0F).setDuration(200).setStartDelay(1000).setListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animator) {}
                            @Override
                            public void onAnimationEnd(Animator animator) {
                                view.findViewById(R.id.failedText).setVisibility(View.GONE);
                                loadingFrame.setVisibility(View.GONE);
                            }
                            @Override
                            public void onAnimationCancel(Animator animator) {}
                            @Override
                            public void onAnimationRepeat(Animator animator) {}
                        }).start();
                    }
                }

            };










            allCategories.put(SETTING_SERVERADDRESS.category, connection);
            allCategories.put(SETTING_USERNAME.category, login);


        }
        return allCategories;
    }
/*
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
*/



    public static Boolean getBoolValue(Context context, SettingID id) {
        return ((BoolSetting) getAllSettings(context).get(id.category).getSetting(id.tag)).getValue(context);
    }

    public static String getStringValue(Context context, SettingID id) {
        return ((TextSetting) getAllSettings(context).get(id.category).getSetting(id.tag)).getValue(context);
    }

    public static void setValue(Context context, SettingID id, boolean newValue) {
        getAllSettings(context).get(id.category).getSetting(id.tag).set(context, newValue);
    }

    public static void setValue(Context context, SettingID id, String newValue) {
        getAllSettings(context).get(id.category).getSetting(id.tag).set(context, newValue);
    }

    public static int getIcon(Context context, SettingID id) {
        return getAllSettings(context).get(id.category).getSetting(id.tag).getIconId();
    }

    public static String getName(Context context, SettingID id) {
        return getAllSettings(context).get(id.category).getSetting(id.tag).getName(context);
    }

    public static String getText(Context context, SettingID id) {
        return getAllSettings(context).get(id.category).getSetting(id.tag).getText(context);
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

    private static class SettingID {
        String tag;
        int category;

        SettingID(int category, String tag) {
            this.category = category;
            this.tag = tag;
        }
    }

}
