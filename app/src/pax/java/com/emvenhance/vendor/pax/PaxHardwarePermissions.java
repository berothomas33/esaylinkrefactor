package com.emvenhance.vendor.pax;

import android.content.pm.PackageManager;
import androidx.fragment.app.FragmentActivity;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.commonlib.utils.PermissionUtils;

/**
 * Neptune Lite hardware permissions required before {@code dal.getIcc()/getMag()/getPicc()}.
 *
 * <p>Must be declared in the pax flavor {@code AndroidManifest.xml} as
 * {@code com.pax.permission.*}. Missing declarations produce ICC#101 / "Not Permission for icc".
 */
final class PaxHardwarePermissions {

    private static final String TAG = "PaxHardwarePermissions";

    static final String ICC = "com.pax.permission.ICC";
    static final String PICC = "com.pax.permission.PICC";
    static final String MAGCARD = "com.pax.permission.MAGCARD";
    static final String PED = "com.pax.permission.PED";
    static final String PRINTER = "com.pax.permission.PRINTER";

    private static final String[] ALL = {ICC, PICC, MAGCARD, PED, PRINTER};

    private PaxHardwarePermissions() {
    }

    static void request(FragmentActivity activity) {
        logGrantState();
        PermissionUtils.getInstance().request(activity, ALL, denyList -> {
            if (denyList.isEmpty()) {
                LogUtils.i(TAG, "PAX hardware permissions granted");
            } else {
                LogUtils.w(TAG, "PAX hardware permissions denied: " + denyList
                        + " — card search will hit ICC#101 / MAG / PICC no-permission");
            }
        });
    }

    static void logGrantState() {
        for (String permission : ALL) {
            int result = BaseApplication.getAppContext().checkSelfPermission(permission);
            LogUtils.i(TAG, permission + " = "
                    + (result == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        }
    }
}
