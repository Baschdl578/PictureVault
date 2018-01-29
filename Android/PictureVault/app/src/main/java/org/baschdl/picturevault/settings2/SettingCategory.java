package org.baschdl.picturevault.settings2;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.baschdl.picturevault.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Category for a Setting
 */
public class SettingCategory implements Comparable<SettingCategory> {
    private int name;
    private Integer sort;
    private HashMap<String, AbstractSetting> settings;
    FrameLayout view;


    public static SettingCategory EMPTY = new SettingCategory(-1, -1);

    public SettingCategory(int name, int sort) {
        this.name = name;
        this.sort = sort;
        this.settings = new HashMap<>();
    }

    public SettingCategory(int name, int sort, AbstractSetting... settings) {
        this.name = name;
        this.sort = sort;
        this.settings = new HashMap<>(settings.length);
        for (AbstractSetting setting: settings) {
            setting.setCategory(this);
            this.settings.put(setting.tag(), setting);
        }
    }

    public AbstractSetting getSetting(String tag) {
        return this.settings.get(tag);
    }

    public String getName(Context context) {
        return context.getString(name);
    }

    public ArrayList<AbstractSetting> allSettings() {
        ArrayList<AbstractSetting> out = new ArrayList<>(settings.size());
        out.addAll(settings.values());
        Collections.sort(out);
        return out;
    }

    public void verify(Context context) {

    };

    public View buildUI(Context context, ViewGroup root) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        FrameLayout category = (FrameLayout) inflater.inflate(R.layout.element_setting_category, root);
        ((TextView) category.findViewById(R.id.moduleName)).setText(context.getString(this.name));

        LinearLayout container = category.findViewById(R.id.settingsContainer);
        Iterator<AbstractSetting> iter = allSettings().iterator();
        while(iter.hasNext()) {
            AbstractSetting setting = iter.next();
            container.addView(setting.buildUI(context, !iter.hasNext()));
        }
        view = category;
        return category;
    }


    @Override
    public int compareTo(@NonNull SettingCategory category) {
        return this.sort.compareTo(category.sort);
    }
}
