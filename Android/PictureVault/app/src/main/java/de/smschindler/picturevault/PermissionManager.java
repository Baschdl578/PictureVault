package de.smschindler.picturevault;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;


/**
 * Manages runtime permissions
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public abstract class PermissionManager {
    public static final String EVENT_PERMISSIONUPDATE = "permissionupdate";
    public static final String EXTRA_PERMISSIONID = "permissionID";

    public static final String ID_GETACCOUNTS = "getaccounts";
    public static final String ID_WRITEEXTERNAL = "writeext";
    public static final String ID_READEXTERNAL = "readext";
    private static PermissionManager instance = null;
    private ArrayList<String> permissions = new ArrayList<>();

    public PermissionManager(String... permissions) {
        Collections.addAll(this.permissions, permissions);
        PermissionManager.instance = this;
    }

    private static String getNameFromID(String ID) {
        if (ID.equals(ID_GETACCOUNTS))
            return MyApplication.getInstance().getApplicationContext().getString(R.string.perm_getaccounts_name);
        if (ID.equals(ID_WRITEEXTERNAL))
            return MyApplication.getInstance().getApplicationContext().getString(R.string.perm_writeexternal_name);
        if (ID.equals(ID_READEXTERNAL))
            return MyApplication.getInstance().getApplicationContext().getString(R.string.perm_readexternal_name);
        return "";
    }

    private static String getDescriptionFromID(String ID) {
        if (ID.equals(ID_GETACCOUNTS))
            return MyApplication.getInstance().getApplicationContext().getString(R.string.perm_getaccounts_descr);
        if (ID.equals(ID_WRITEEXTERNAL))
            return MyApplication.getInstance().getApplicationContext().getString(R.string.perm_writeexternal_descr);
        if (ID.equals(ID_READEXTERNAL))
            return MyApplication.getInstance().getApplicationContext().getString(R.string.perm_readexternal_descr);
        return "";
    }

    private static boolean checkPermission(String ID) {
        if (ID.equals(ID_GETACCOUNTS))
            return ActivityCompat.checkSelfPermission(MyApplication.getInstance().getApplicationContext(), Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED;
        if (ID.equals(ID_WRITEEXTERNAL))
            return ActivityCompat.checkSelfPermission(MyApplication.getInstance().getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return ID.equals(ID_READEXTERNAL) && ActivityCompat.checkSelfPermission(MyApplication.getInstance().getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private static void getPermission(String ID) {
        if (ID.equals(ID_GETACCOUNTS))
            ActivityCompat.requestPermissions(AppActivity.getContext(), new String[]{Manifest.permission.GET_ACCOUNTS}, AppActivity.GET_ACCOUNTS_PERM_CODE);
        if (ID.equals(ID_WRITEEXTERNAL))
            ActivityCompat.requestPermissions(AppActivity.getContext(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, AppActivity.WRITE_EXTERNAL_PERM_CODE);
        if (ID.equals(ID_READEXTERNAL))
            ActivityCompat.requestPermissions(AppActivity.getContext(), new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, AppActivity.READ_EXTERNAL_PERM_CODE);
    }

    public abstract void onAllGranted();

    public abstract void onFailed();

    private boolean allGranted() {
        boolean allgranted = true;
        for (String perm : permissions) {
            if (!checkPermission(perm)) allgranted = false;
        }
        return allgranted;
    }

    public void execute(Activity context) {
        if (allGranted() || Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            onAllGranted();
            return;
        }

        // Create and show the dialog.
        Bundle args = new Bundle();
        args.putStringArrayList(EXTRA_PERMISSIONID, permissions);
        PermissionDialog dialog = new PermissionDialog();
        dialog.setArguments(args);
        dialog.show(context.getFragmentManager(), "dialog");
    }

    public static class PermissionDialog extends DialogFragment {
        private ArrayList<String> permissions = new ArrayList<>();

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (savedInstanceState != null && !savedInstanceState.isEmpty() && savedInstanceState.containsKey(EXTRA_PERMISSIONID)) {
                permissions = savedInstanceState.getStringArrayList(EXTRA_PERMISSIONID);
            } else {
                permissions = getArguments().getStringArrayList(EXTRA_PERMISSIONID);
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(getString(R.string.needspermissions)).setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (PermissionManager.instance.allGranted()) {
                        instance.onAllGranted();
                    } else instance.onFailed();
                }
            })
                    .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            instance.onFailed();
                        }
                    });

            Dialog dialog = builder.create();
            ScrollView scroll = new ScrollView(MyApplication.getInstance().getApplicationContext());
            ScrollView.LayoutParams scrollParams = new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            LinearLayout frame = new LinearLayout(MyApplication.getInstance().getApplicationContext());
            LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            frame.setOrientation(LinearLayout.VERTICAL);
            frame.setPadding(30, 30, 30, 16);

            for (String permissionID : permissions) {
                SinglePermissionUI permUI = new SinglePermissionUI(MyApplication.getInstance().getApplicationContext());
                permUI.populate(permissionID);
                frame.addView(permUI);
            }
            frame.setLayoutParams(topParams);
            frame.requestLayout();
            scroll.addView(frame);
            scroll.setLayoutParams(scrollParams);
            scroll.requestLayout();
            ((AlertDialog) dialog).setView(scroll);
            return dialog;

        }

        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putStringArrayList(EXTRA_PERMISSIONID, permissions);
        }


        public class SinglePermissionUI extends RelativeLayout implements View.OnClickListener {
            private String permID;

            public SinglePermissionUI(Context context) {
                super(context);
                init();
            }

            public SinglePermissionUI(Context context, AttributeSet attrs) {
                super(context, attrs);
                init();
            }

            public SinglePermissionUI(Context context, AttributeSet attrs, int defStyleAttr) {
                super(context, attrs, defStyleAttr);
                init();
            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public SinglePermissionUI(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
                super(context, attrs, defStyleAttr, defStyleRes);
                init();
            }


            private void init() {
                inflate(getContext(), R.layout.element_permission, this);
                this.setOnClickListener(this);
            }

            private void populate(String permissionID) {
                permID = permissionID;
                LocalBroadcastManager.getInstance(getContext()).registerReceiver(new permissionUpdateReceiver(), new IntentFilter(EVENT_PERMISSIONUPDATE));
                ((TextView) findViewById(R.id.name)).setText(getNameFromID(permissionID));
                ((TextView) findViewById(R.id.descr)).setText(getDescriptionFromID(permissionID));

                if (checkPermission(permissionID)) {
                    ((ImageView) findViewById(R.id.status)).setImageResource(R.drawable.done);
                }
                this.requestLayout();
            }

            public void onClick(View view) {
                if (!checkPermission(permID)) {
                    getPermission(permID);
                } else {
                    setIcon(true);
                }
            }

            public void setIcon(boolean hasPermission) {
                if (hasPermission) {
                    ((ImageView) findViewById(R.id.status)).setImageResource(R.drawable.done);
                } else {
                    ((ImageView) findViewById(R.id.status)).setImageResource(R.drawable.clear);
                }
            }

            public class permissionUpdateReceiver extends BroadcastReceiver {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String ident = intent.getStringExtra(EXTRA_PERMISSIONID);
                    if (permID.equals(ident)) {
                        setIcon(checkPermission(permID));
                    }
                }
            }

        }
    }

}
