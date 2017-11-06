package org.baschdl.picturevault.settings;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.baschdl.picturevault.R;


/**
 * UI Element to encompass settings for an action
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class ModuleSettings extends LinearLayout {


    public ModuleSettings(Context context) {
        super(context);
        init();
    }

    public ModuleSettings(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ModuleSettings(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ModuleSettings(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public void addSetting(SettingObject setting, boolean last) {
        if (!setting.isShow()) return;
        SettingUIElement set = new SettingUIElement(getContext());
        if (setting instanceof TextSettingObject) {
            set.populate((TextSettingObject) setting, last);
        } else {
            set.populate((BoolSettingObject) setting, last);
        }
        LayoutParams paramsSet = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ((LinearLayout) findViewById(R.id.settingsContainer)).addView(set);
        set.setLayoutParams(paramsSet);
    }

    public void setCategory(String category) {
        ((TextView) this.findViewById(R.id.moduleName)).setText(category);
    }

    private void init() {
        inflate(getContext(), R.layout.element_setting_category, this);
    }

}
