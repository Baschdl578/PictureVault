package de.smschindler.picturevault.settings2;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import android.view.View;

/**
 * Created by baschdl on 07.09.17.
 */

public abstract class AbstractSetting implements Comparable<AbstractSetting> {
    private int name;
    private int text;
    private String tag;
    private Integer sortId;
    private boolean hideText;
    private int iconId;
    private boolean showUI;
    private SettingCategory category;


    public AbstractSetting(int name, int text, int iconId, String tag, boolean hideText, boolean showUI, int sort) {
        this.name = name;
        this.text = text;
        this.iconId = iconId;
        this.tag = tag;
        this.sortId = sort;
        this.hideText = hideText;
        this.showUI = showUI;
    }

    public AbstractSetting(int name, int text, String tag) {
        this.name = name;
        this.text = text;
        this.iconId = -1;
        this.tag = tag;
        this.sortId = -1;
        this.hideText = false;
        this.showUI = true;
    }

    public AbstractSetting(int name, int text, int iconId, String tag, int sort) {
        this.name = name;
        this.text = text;
        this.iconId = iconId;
        this.tag = tag;
        this.sortId = sort;
        this.hideText = false;
        this.showUI = true;
    }

    public AbstractSetting(int name, int text, int iconId, String tag, boolean hideText, int sort) {
        this.name = name;
        this.text = text;
        this.iconId = iconId;
        this.tag = tag;
        this.sortId = sort;
        this.hideText = hideText;
        this.showUI = true;
    }


    public boolean set(Context context, boolean newValue) {
        if (checkInput(context, newValue)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putBoolean(this.tag, newValue);
            edit.apply();
            return true;
        }
        return false;
    }

    public boolean set(Context context, String newValue) {
        if (checkInput(context, newValue)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(this.tag, newValue);
            edit.apply();
            return true;
        }
        return false;
    }

    public void setCategory(SettingCategory category) {
        this.category = category;
    }

    public abstract Object getValue(Context context);

    abstract boolean checkInput(Context context, String newValue);

    abstract boolean checkInput(Context context, boolean newValue);

    abstract View buildUI(Context context, boolean isLast);

    public String getName(Context context) {
        return context.getString(name);
    }

    public String getText(Context context) {
        return context.getString(text);
    }

    public boolean hideText() {
        return hideText;
    }

    public int getIconId() {
        return iconId;
    }

    public boolean showUI() {
        return showUI;
    }

    public String tag() {
        return tag;
    }

    public boolean isSet(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.contains(this.tag);
    }

    @Override
    public int compareTo(@NonNull AbstractSetting o) {
        return this.sortId.compareTo(o.sortId);
    }
}
