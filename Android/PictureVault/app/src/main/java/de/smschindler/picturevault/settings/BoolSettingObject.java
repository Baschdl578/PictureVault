package de.smschindler.picturevault.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Setting object for a boolean setting
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class BoolSettingObject extends SettingObject {
    public static String FALSE = "false";
    public static String TRUE = "true";


    public BoolSettingObject(String name, String text, String tag, boolean hidden, int iconId, int action, String category, int sort, int categorySort) {
        super(name, text, tag, hidden, iconId, action, true, category, sort, categorySort);
    }

    public BoolSettingObject(String name, String text, String tag, boolean hidden, int iconId, String category, int sort, int categorySort) {
        super(name, text, tag, hidden, iconId, SettingObject.ACTION_BOOLDEFAULT, true, category, sort, categorySort);
    }

    public BoolSettingObject(String name, String text, String tag, boolean hidden, int iconId, int action, boolean show, String category, int sort, int categorySort) {
        super(name, text, tag, hidden, iconId, action, show, category, sort, categorySort);
    }

    public BoolSettingObject(String name, String text, String tag, boolean hidden, int iconId, boolean show, String category, int sort, int categorySort) {
        super(name, text, tag, hidden, iconId, SettingObject.ACTION_BOOLDEFAULT, show, category, sort, categorySort);
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
        edit.putBoolean(getTag(), val);
        edit.apply();
    }

    public Boolean getValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(getTag(), false);
    }
}
