package com.emvenhance.core.util

import android.util.Log
import com.emvenhance.core.BuildConfig

/**
 * Debug-gated log helper — same convention as `EmvDeviceImpl`'s APDU trans log: never writes
 * anything in a release build. Named `EmvLog`, not `LogUtils`, so it doesn't collide with
 * `com.pax.commonlib.utils.LogUtils` already imported in PAX-flavor files.
 */
object EmvLog {

    private const val TAG = "EmvEnhance"

    @JvmStatic
    fun d(msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, msg)
        }
    }
}
