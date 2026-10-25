package com.emvenhance.core.util;

import android.util.Log;
import com.emvenhance.core.BuildConfig;

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
