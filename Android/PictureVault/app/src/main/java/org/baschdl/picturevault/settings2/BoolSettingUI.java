package org.baschdl.picturevault.settings2;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.res.ResourcesCompat;
import android.text.SpannableString;
import android.text.method.PasswordTransformationMethod;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.baschdl.picturevault.MyApplication;
import org.baschdl.picturevault.R;

/**
 * Created by baschdl on 07.09.17.
 */

public class BoolSettingUI extends RelativeLayout implements View.OnClickListener {
    public static final String UPDATE_EVENT = "update_setting";
    public static final String EXTRA_SETTINGTAG = "settingtag";
    private BoolSetting settingObject;


    public BoolSettingUI(Context context) {
        super(context);
    }

    public BoolSettingUI(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BoolSettingUI(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public BoolSettingUI(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    void init(BoolSetting setting, boolean isLast) {
        inflate(getContext(), R.layout.element_bool_setting, this);
        //LocalBroadcastManager.getInstance(getContext()).registerReceiver(new BoolSettingUI.updateReceiver(), new IntentFilter(UPDATE_EVENT));
        this.setOnClickListener(this);
        ((ImageView) this.findViewById(R.id.icon)).setImageResource(settingObject.getIconId());
        ((TextView) this.findViewById(R.id.name)).setText(settingObject.getName(getContext()));

        if (!isLast) {
            findViewById(R.id.divider).setVisibility(VISIBLE);
            findViewById(R.id.divider).getLayoutParams().width = Resources.getSystem().getDisplayMetrics().widthPixels - this.findViewById(R.id.icon).getLayoutParams().width;
        }
        this.settingObject = setting;
        ((TextView) this.findViewById(R.id.setting)).setText(settingObject.getText(getContext()));
        ((TextView) this.findViewById(R.id.setting)).setText(settingObject.getText(getContext()));
        Switch sw = ((Switch) this.findViewById(R.id.switcher));
        sw.setChecked(settingObject.getValue(getContext()));
        sw.setVisibility(View.VISIBLE);
        this.requestLayout();
    }

    private void update(BoolSetting settingObject) {
        ((Switch) this.findViewById(R.id.switcher)).setChecked(settingObject.getValue(getContext()));
    }

    public void onClick(View v) {
        action();
    }


    private void action() {
        Switch box = this.findViewById(R.id.switcher);
        Boolean old;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance().getApplicationContext());

        old = prefs.getBoolean(settingObject.tag(), false);
        settingObject.set(getContext(), !old);
        box.setChecked(!old);
    }
   /*

    public class updateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String settingTag = intent.getStringExtra(EXTRA_SETTINGTAG);

            if (settingTag.equals(settingObject.tag())) {
                update(settingObject);
            }
        }
    } */
}
