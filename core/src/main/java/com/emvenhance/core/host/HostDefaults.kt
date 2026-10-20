package com.emvenhance.core.host

import android.util.Log
import kotlinx.coroutines.delay

/** Simple host / printer stubs for demos and unfinished wiring. */
object HostDefaults {

    @JvmStatic
    fun approveAlways(): CommunicationBehavior = CommunicationBehavior {
        delay(400)
        AuthResult.approved("123456", "00", null, null)
    }

    @JvmStatic
    fun declineUntilWired(): CommunicationBehavior = CommunicationBehavior {
        AuthResult.declined("96", "Host not wired")
    }

    @JvmStatic
    fun logPrinter(tag: String): PrinterBehavior = PrinterBehavior { lines ->
        for (line in lines) {
            Log.i(tag, line)
        }
    }
}
