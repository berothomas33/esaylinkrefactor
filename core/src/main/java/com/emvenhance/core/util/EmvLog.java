package com.emvenhance.core.util;

import android.util.Log;
import com.emvenhance.core.BuildConfig;

/**
 * Debug-gated log helper — same convention as {@code EmvDeviceImpl}'s APDU trans log: never
 * writes anything in a release build. Named {@code EmvLog}, not {@code LogUtils}, so it doesn't
 * collide with {@code com.pax.commonlib.utils.LogUtils} already imported in PAX-flavor files.
 */
public final class EmvLog {

    private static final String TAG = "EmvEnhance";

    private EmvLog() {
    }

    public static void d(String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
