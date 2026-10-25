package com.emvenhance.vendor.pax;

import android.content.pm.PackageManager;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;

public final class PaxHardwarePermissions {

    private static final String TAG = "PaxHardwarePermissions";

    static final String ICC = "com.pax.permission.ICC";
    static final String PICC = "com.pax.permission.PICC";
    static final String MAGCARD = "com.pax.permission.MAGCARD";
    static final String PED = "com.pax.permission.PED";
    static final String PRINTER = "com.pax.permission.PRINTER";

    private static final String[] ALL = {ICC, PICC, MAGCARD, PED, PRINTER};

    private PaxHardwarePermissions() {
    }

    static String[] all() {
        return ALL.clone();
    }

    public static void logGrantState() {
        for (String permission : ALL) {
            int result = BaseApplication.getAppContext().checkSelfPermission(permission);
            LogUtils.i(TAG, permission + " = "
                    + (result == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        }
    }
}
