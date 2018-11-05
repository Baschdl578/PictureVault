package de.smschindler.picturevault;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity archetype
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
@SuppressLint("Registered")
public class AppActivity extends AppCompatActivity {
    public static final int READ_EXTERNAL_PERM_CODE = 1;
    public static final int WRITE_EXTERNAL_PERM_CODE = 2;
    public static final int GET_ACCOUNTS_PERM_CODE = 3;
    private static AppActivity context;


    public static AppActivity getContext() {
        return context;
    }

    public static void setContext(AppActivity cont) {
        context = cont;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case GET_ACCOUNTS_PERM_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(PermissionManager.EVENT_PERMISSIONUPDATE);
                    intent.putExtra(PermissionManager.EXTRA_PERMISSIONID, PermissionManager.ID_GETACCOUNTS);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                }
                break;
            case READ_EXTERNAL_PERM_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(PermissionManager.EVENT_PERMISSIONUPDATE);
                    intent.putExtra(PermissionManager.EXTRA_PERMISSIONID, PermissionManager.ID_READEXTERNAL);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                }
                break;
            case WRITE_EXTERNAL_PERM_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(PermissionManager.EVENT_PERMISSIONUPDATE);
                    intent.putExtra(PermissionManager.EXTRA_PERMISSIONID, PermissionManager.ID_WRITEEXTERNAL);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                }
                break;
            default:
                break;
        }
    }

    protected int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
    }
}
