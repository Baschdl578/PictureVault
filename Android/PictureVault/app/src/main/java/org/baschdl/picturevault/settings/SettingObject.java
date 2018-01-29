package org.baschdl.picturevault.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Abstract setting object
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public abstract class SettingObject {
    public static final int ACTION_BOOLDEFAULT = 0;
    public static final int ACTION_TEXTDEFAULT = 1;
    public static final int ACTION_WIFINAME = 2;
    public static final int ACTION_DOSYNC = 3;

    private String name;
    private String text;
    private String tag;
    private String category;
    private Integer sortId;
    private Integer categorySort;
    private boolean hide;
    private int iconId;
    private int action;
    private boolean show;

    SettingObject(String name, String text, String tag, boolean hidden, int iconId, int action, boolean showInApp, String category, Integer sort, Integer categorySort) {
        this.name = name;
        this.text = text;
        this.tag = tag;
        this.hide = hidden;
        this.iconId = iconId;
        this.action = action;
        this.show = showInApp;
        this.category = category;
        this.sortId = sort;
        this.categorySort = categorySort;
    }

    public boolean isShow() {
        return show;
    }

    public void setShow(boolean show) {
        this.show = show;
    }

    public String getName() {
        return name;
    }

    public String getText() {
        return text;
    }

    public String getTag() {
        return tag;
    }

    public abstract void set(Context context, Object value);

    public boolean isHidden() {
        return hide;
    }

    public int getIconId() {
        return iconId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getSortId() {
        return sortId;
    }

    public void setSortId(Integer sortId) {
        this.sortId = sortId;
    }

    public Integer getCategorySort() {
        return categorySort;
    }

    public void setCategorySort(Integer categorySort) {
        this.categorySort = categorySort;
    }

    public boolean isSet(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.contains(getTag());
    }

    public abstract Object getValue(Context context);

    public int getAction() {
        return action;
    }
}
