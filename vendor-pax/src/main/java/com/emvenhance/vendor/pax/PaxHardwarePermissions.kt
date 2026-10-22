package com.emvenhance.vendor.pax

import android.content.pm.PackageManager
import com.pax.commonlib.application.BaseApplication
import com.pax.commonlib.utils.LogUtils

/**
 * Neptune Lite hardware permissions required before `dal.getIcc()/getMag()/getPicc()`.
 *
 * Must be declared in vendor-pax's `AndroidManifest.xml` as `com.pax.permission.*`. Missing
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

    /** All of them, for `PosTerminal.requiredPermissions()`: a screen asks for them. */
    internal fun all(): Array<String> = ALL.clone()

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
