package com.emvenhance.vendor.pax

import android.content.pm.PackageManager
import androidx.fragment.app.FragmentActivity
import com.pax.commonlib.application.BaseApplication
import com.pax.commonlib.utils.LogUtils
import com.pax.commonlib.utils.PermissionUtils

/**
 * Neptune Lite hardware permissions required before `dal.getIcc()/getMag()/getPicc()`.
 *
 * Must be declared in the pax flavor `AndroidManifest.xml` as `com.pax.permission.*`. Missing
 * declarations produce ICC#101 / "Not Permission for icc".
 */
object PaxHardwarePermissions {

    private const val TAG = "PaxHardwarePermissions"

    internal const val ICC = "com.pax.permission.ICC"
    internal const val PICC = "com.pax.permission.PICC"
    internal const val MAGCARD = "com.pax.permission.MAGCARD"
    internal const val PED = "com.pax.permission.PED"
    internal const val PRINTER = "com.pax.permission.PRINTER"

    private val ALL = arrayOf(ICC, PICC, MAGCARD, PED, PRINTER)

    @JvmStatic
    fun request(activity: FragmentActivity) {
        logGrantState()
        PermissionUtils.getInstance().request(activity, ALL) { denyList ->
            if (denyList.isEmpty()) {
                LogUtils.i(TAG, "PAX hardware permissions granted")
            } else {
                LogUtils.w(
                    TAG,
                    "PAX hardware permissions denied: " + denyList +
                        " — card search will hit ICC#101 / MAG / PICC no-permission",
                )
            }
        }
    }

    @JvmStatic
    fun logGrantState() {
        for (permission in ALL) {
            val result = BaseApplication.getAppContext().checkSelfPermission(permission)
            LogUtils.i(
                TAG,
                permission + " = " + if (result == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED",
            )
        }
    }
}
