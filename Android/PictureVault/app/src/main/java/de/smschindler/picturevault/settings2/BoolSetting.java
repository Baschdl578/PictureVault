package de.smschindler.picturevault.settings2;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

/**
 * Created by baschdl on 07.09.17.
 */

public class BoolSetting extends AbstractSetting {
    public static String FALSE = "false";
    public static String TRUE = "true";


    public BoolSetting(int name, int text, int iconId, String tag, boolean showUI, int sort) {
        super(name, text, iconId, tag, false, showUI, sort);
    }

    public BoolSetting(int name, int text, String tag) {
        super(name, text, tag);
    }

    public BoolSetting(int name, int text, int iconId, String tag, int sort) {
        super(name, text, iconId, tag, false, sort);
    }

    public void set(Context context, Object value) {
        Boolean val = false;
        if (value instanceof Boolean) {
            val = (Boolean) value;
        } else {
            Log.i("Settings", "value not instance of boolean");
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(tag(), val);
        edit.apply();
    }

    @Override
    public Boolean getValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(tag(), false);
    }

    @Override
    boolean checkInput(Context context, String newValue) {
        return true;
    }

    @Override
    boolean checkInput(Context context, boolean newValue) {
        return true;
    }

    @Override
    View buildUI(Context context, boolean isLast) {
        return null;
    }
}
