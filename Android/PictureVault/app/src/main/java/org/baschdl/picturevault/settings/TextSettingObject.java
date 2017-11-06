package org.baschdl.picturevault.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.baschdl.picturevault.R;

/**
 * Setting object for a string setting
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class TextSettingObject extends SettingObject {

    public TextSettingObject(String name, String text, String tag, boolean hidden, int iconId, int action, String category, int sort, int categorySort) {
        super(name, text, tag, hidden, iconId, action, true, category, sort, categorySort);
    }

    public TextSettingObject(String name, String text, String tag, boolean hidden, int iconId, String category, int sort, int categorySort) {
        super(name, text, tag, hidden, iconId, SettingObject.ACTION_TEXTDEFAULT, true, category, sort, categorySort);
    }

    public TextSettingObject(String name, String text, String tag, boolean hidden, int iconId, int action, boolean show, String category, int sort, int categorySort) {
        super(name, text, tag, hidden, iconId, action, show, category, sort, categorySort);
    }

    public TextSettingObject(String name, String text, String tag, boolean hidden, int iconId, boolean show, String category, int sort, int categorySort) {
        super(name, text, tag, hidden, iconId, SettingObject.ACTION_TEXTDEFAULT, show, category, sort, categorySort);
    }

    public void set(Context context, Object value) {
        String val = "";
        if (value instanceof String) {
            val = (String) value;
        } else {
            Log.i("Settings", "value not instance of string");
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(getTag(), val);
        edit.apply();
    }

    public String getValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(getTag(), context.getString(R.string.notSet));
    }
}
