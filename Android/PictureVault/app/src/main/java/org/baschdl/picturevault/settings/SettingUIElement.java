package org.baschdl.picturevault.settings;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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

import org.baschdl.picturevault.AppActivity;
import org.baschdl.picturevault.MyApplication;
import org.baschdl.picturevault.PermissionManager;
import org.baschdl.picturevault.R;
import org.baschdl.picturevault.Utilities;

/**
 * Universal UI element for a single setting
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class SettingUIElement extends RelativeLayout implements View.OnClickListener {
    public static final String UPDATE_EVENT = "update_setting";
    public static final String EXTRA_SETTINGTAG = "settingtag";
    public static final String EXTRA_ISBOOLSETTING = "isboolsetting";
    private SettingObject settingObject;


    public SettingUIElement(Context context) {
        super(context);
        init();
    }

    public SettingUIElement(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SettingUIElement(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SettingUIElement(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }


    private void init() {
        inflate(getContext(), R.layout.element_bool_setting, this);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(new updateReceiver(), new IntentFilter(UPDATE_EVENT));
        this.setOnClickListener(this);
    }

    private void update(TextSettingObject settingObject) {
        ((TextView) this.findViewById(R.id.setting)).setText(settingObject.getValue(MyApplication.getInstance().getApplicationContext()));
    }

    private void update(BoolSettingObject settingObject) {
        ((Switch) this.findViewById(R.id.switcher)).setChecked(settingObject.getValue(getContext()));
    }

    public void populate(TextSettingObject settingObject, boolean isLast) {
        ((ImageView) this.findViewById(R.id.icon)).setImageResource(settingObject.getIconId());
        ((TextView) this.findViewById(R.id.name)).setText(settingObject.getName());

        if (!isLast) {
            findViewById(R.id.divider).setVisibility(VISIBLE);
            findViewById(R.id.divider).getLayoutParams().width = Resources.getSystem().getDisplayMetrics().widthPixels - this.findViewById(R.id.icon).getLayoutParams().width;
        }
        this.settingObject = settingObject;
        ((TextView) this.findViewById(R.id.setting)).setText(settingObject.getValue(MyApplication.getInstance().getApplicationContext()));
        if (settingObject.isHidden()) {
            ((TextView) this.findViewById(R.id.setting)).setTransformationMethod(PasswordTransformationMethod.getInstance());
        }

    }

    public void populate(BoolSettingObject settingObject, boolean isLast) {
        ((ImageView) this.findViewById(R.id.icon)).setImageResource(settingObject.getIconId());
        ((TextView) this.findViewById(R.id.name)).setText(settingObject.getName());

        if (!isLast) {
            findViewById(R.id.divider).setVisibility(VISIBLE);
            findViewById(R.id.divider).getLayoutParams().width = Resources.getSystem().getDisplayMetrics().widthPixels - this.findViewById(R.id.icon).getLayoutParams().width;
        }
        this.settingObject = settingObject;
        ((TextView) this.findViewById(R.id.setting)).setText(settingObject.getText());
        Switch sw = ((Switch) this.findViewById(R.id.switcher));
        sw.setChecked(settingObject.getValue(MyApplication.getInstance().getApplicationContext()));
        sw.setVisibility(View.VISIBLE);
        this.requestLayout();
    }

    public void onClick(View v) {
        switch (settingObject.getAction()) {
            case SettingObject.ACTION_BOOLDEFAULT:
                actBoolDef();
                break;
            case SettingObject.ACTION_TEXTDEFAULT:
                actTextDef();
                break;
            case SettingObject.ACTION_WIFINAME:
                actWifiChange();
                break;
            case SettingObject.ACTION_DOSYNC:
                actDoSync();
                break;
            default:
                break;
        }
    }

    private void actDoSync() {
        new MyPermissionManager(PermissionManager.ID_GETACCOUNTS, PermissionManager.ID_WRITEEXTERNAL, PermissionManager.ID_READEXTERNAL).execute(AppActivity.getContext());
    }

    private void actBoolDef() {
        Switch box = this.findViewById(R.id.switcher);
        Boolean old;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance().getApplicationContext());

        old = prefs.getBoolean(settingObject.getTag(), false);
        settingObject.set(getContext(), !old);
        box.setChecked(!old);
    }

    private void actTextDef() {
        FragmentManager manager = ((Activity) getContext()).getFragmentManager();
        new SettingDialog(settingObject).show(manager, settingObject.getTag());
    }

    private void actWifiChange() {
        WifiManager wifiMgr = (WifiManager) MyApplication.getInstance().getApplicationContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        String wifiName = wifiInfo.getSSID();
        settingObject.set(getContext(), wifiName);
        ((TextView) this.findViewById(R.id.setting)).setText(((TextSettingObject) settingObject).getValue(MyApplication.getInstance().getApplicationContext()));
    }


    @SuppressLint("ValidFragment")
    private class SettingDialog extends DialogFragment {
        SettingObject settingObject;
        EditText text;

        public SettingDialog(SettingObject set) {
            settingObject = set;
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    settingObject.set(MyApplication.getInstance().getApplicationContext(), text.getText().toString());
                    ((TextView) findViewById(R.id.setting)).setText((String) settingObject.getValue(MyApplication.getInstance().getApplicationContext()));
                    dismiss();
                }
            }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dismiss();
                }
            });

            Dialog dialog = builder.create();
            FrameLayout frame = new FrameLayout(MyApplication.getInstance().getApplicationContext());
            frame.setPadding(30, 30, 30, 16);

            LinearLayout topLay = new LinearLayout(MyApplication.getInstance().getApplicationContext());
            topLay.setOrientation(LinearLayout.VERTICAL);
            FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            topLay.setLayoutParams(topParams);

            TextView description = new TextView(MyApplication.getInstance().getApplicationContext());
            description.setText(settingObject.getName());
            description.setTextColor(ResourcesCompat.getColor(getResources(), R.color.grey600, null));
            LinearLayout.LayoutParams descrParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descrParams.bottomMargin = 10;
            description.setLayoutParams(descrParams);

            topLay.addView(description);

            text = new EditText(MyApplication.getInstance().getApplicationContext());
            if (settingObject.isHidden()) {
                text.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            text.setLayoutParams(textParams);
            String tempString = settingObject.getText();
            SpannableString spanString = new SpannableString(tempString);
            spanString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spanString.length(), 0);
            text.setHint(spanString);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance().getApplicationContext());
            if (preferences.contains(settingObject.getTag())) {
                text.setText(preferences.getString(settingObject.getTag(), null));
            }

            topLay.addView(text);

            frame.addView(topLay);
            frame.requestLayout();
            ((AlertDialog) dialog).setView(frame);
            return dialog;
        }

    }

    public class updateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String settingTag = intent.getStringExtra(EXTRA_SETTINGTAG);
            Boolean isBoolSetting = intent.getBooleanExtra(EXTRA_ISBOOLSETTING, false);

            if (settingTag.equals(settingObject.getTag())) {
                if (isBoolSetting) {
                    update((BoolSettingObject) settingObject);
                } else {
                    update((TextSettingObject) settingObject);
                }
            }
        }
    }

    private class MyPermissionManager extends PermissionManager {

        MyPermissionManager(String... perms) {
            super(perms);
        }

        public void onAllGranted() {
            Utilities.activateSync(getContext());
            actBoolDef();
        }

        public void onFailed() {
            SettingsManager.setValue(getContext(), SettingsManager.SETTING_DOSYNC, false);
            ((Switch) SettingUIElement.this.findViewById(R.id.switcher)).setChecked(false);
        }
    }
}
