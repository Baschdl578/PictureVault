package org.baschdl.picturevault.settings2;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;

import org.baschdl.picturevault.R;

/**
 * Created by baschdl on 07.09.17.
 */

public class TextSetting extends AbstractSetting {

    public TextSetting(int name, int text, int iconId, String tag, boolean hideText, boolean showUI, int sort) {
        super(name, text, iconId, tag, hideText, showUI, sort);
    }

    public TextSetting(int name, int text, String tag) {
        super(name, text, tag);
    }

    public TextSetting(int name, int text, int iconId, String tag, int sort) {
        super(name, text, iconId, tag, sort);
    }

    public TextSetting(int name, int text, int iconId, String tag, boolean hideText, int sort) {
        super(name, text, iconId, tag, hideText, sort);
    }

    @Override
    public String getValue(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(tag(), context.getString(R.string.notSet));
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
        TextSettingUI ui = new TextSettingUI(context);
        ui.init(this, isLast);
        return ui;
    }
}
