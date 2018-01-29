package org.baschdl.picturevault.settings2;

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
public class TextSettingUI extends RelativeLayout implements View.OnClickListener {
    public static final String UPDATE_EVENT = "update_setting";
    public static final String EXTRA_SETTINGTAG = "settingtag";
    private TextSetting settingObject;


    public TextSettingUI(Context context) {
        super(context);
    }

    public TextSettingUI(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextSettingUI(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TextSettingUI(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    void init(TextSetting setting, boolean isLast) {
        inflate(getContext(), R.layout.element_text_setting, this);
        //LocalBroadcastManager.getInstance(getContext()).registerReceiver(new updateReceiver(), new IntentFilter(UPDATE_EVENT));
        this.setOnClickListener(this);
        ((ImageView) this.findViewById(R.id.icon)).setImageResource(settingObject.getIconId());
        ((TextView) this.findViewById(R.id.name)).setText(settingObject.getName(getContext()));

        if (!isLast) {
            findViewById(R.id.divider).setVisibility(VISIBLE);
            findViewById(R.id.divider).getLayoutParams().width = Resources.getSystem().getDisplayMetrics().widthPixels - this.findViewById(R.id.icon).getLayoutParams().width;
        }
        this.settingObject = setting;
        ((TextView) this.findViewById(R.id.setting)).setText(settingObject.getValue(MyApplication.getInstance().getApplicationContext()));
        if (settingObject.hideText()) {
            ((TextView) this.findViewById(R.id.setting)).setTransformationMethod(PasswordTransformationMethod.getInstance());
        }
    }

    private void update(TextSetting settingObject) {
        ((TextView) this.findViewById(R.id.setting)).setText(settingObject.getValue(MyApplication.getInstance().getApplicationContext()));
    }



    public void onClick(View v) {
        action();
    }

    private void actDoSync() {
        new MyPermissionManager(PermissionManager.ID_GETACCOUNTS, PermissionManager.ID_WRITEEXTERNAL, PermissionManager.ID_READEXTERNAL).execute(AppActivity.getContext());
    }


    private void action() {
        FragmentManager manager = ((Activity) getContext()).getFragmentManager();
        new SettingDialog((TextSetting) settingObject).show(manager, settingObject.tag());
    }

    private void actWifiChange() {
        WifiManager wifiMgr = (WifiManager) MyApplication.getInstance().getApplicationContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        String wifiName = wifiInfo.getSSID();
        settingObject.set(getContext(), wifiName);
        ((TextView) this.findViewById(R.id.setting)).setText(((TextSetting) settingObject).getValue(MyApplication.getInstance().getApplicationContext()));
    }


    @SuppressLint("ValidFragment")
    private class SettingDialog extends DialogFragment {
        TextSetting settingObject;
        EditText text;

        public SettingDialog(TextSetting set) {
            settingObject = set;
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    settingObject.set(MyApplication.getInstance().getApplicationContext(), text.getText().toString());
                    ((TextView) findViewById(R.id.setting)).setText(settingObject.getValue(TextSettingUI.this.getContext()));
                    dismiss();
                }
            }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dismiss();
                }
            });

            Dialog dialog = builder.create();
            FrameLayout frame = new FrameLayout(TextSettingUI.this.getContext());
            frame.setPadding(30, 30, 30, 16);

            LinearLayout topLay = new LinearLayout(TextSettingUI.this.getContext());
            topLay.setOrientation(LinearLayout.VERTICAL);
            FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            topLay.setLayoutParams(topParams);

            TextView description = new TextView(TextSettingUI.this.getContext());
            description.setText(settingObject.getName(TextSettingUI.this.getContext()));
            description.setTextColor(ResourcesCompat.getColor(getResources(), R.color.grey600, null));
            LinearLayout.LayoutParams descrParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descrParams.bottomMargin = 10;
            description.setLayoutParams(descrParams);

            topLay.addView(description);

            text = new EditText(TextSettingUI.this.getContext());
            if (settingObject.hideText()) {
                text.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            text.setLayoutParams(textParams);
            String tempString = settingObject.getText(TextSettingUI.this.getContext());
            SpannableString spanString = new SpannableString(tempString);
            spanString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spanString.length(), 0);
            text.setHint(spanString);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(TextSettingUI.this.getContext());
            if (preferences.contains(settingObject.tag())) {
                text.setText(preferences.getString(settingObject.tag(), null));
            }

            topLay.addView(text);

            frame.addView(topLay);
            frame.requestLayout();
            ((AlertDialog) dialog).setView(frame);
            return dialog;
        }

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
    }
    */

    private class MyPermissionManager extends PermissionManager {

        MyPermissionManager(String... perms) {
            super(perms);
        }

        public void onAllGranted() {
            Utilities.activateSync(getContext());
            action();
        }

        public void onFailed() {
            org.baschdl.picturevault.settings2.SettingsManager.setValue(getContext(), SettingsManager.SETTING_DOSYNC, false);
            ((Switch) TextSettingUI.this.findViewById(R.id.switcher)).setChecked(false);
        }
    }
}
