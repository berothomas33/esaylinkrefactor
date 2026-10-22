package com.emvenhance.vendor

import android.app.Application
import com.emvenhance.core.terminal.PosTerminal
import com.emvenhance.emvflow.runtime.EmvFlowRuntime
import com.emvenhance.vendor.pax.PaxTerminal

/**
 * PAX flavor's factory — the only place in this flavor that knows [PaxTerminal]'s name.
 *
 * UI and core never import vendor types — only [PosTerminal].
 */
object TerminalFactory {

    /**
     * PAX needs Neptune DAL + EMV JNI libs before any kernel call. [EmvFlowRuntime.init] is the
     * documented bootstrap (NeptuneLiteUser.getDal, then `EmvUtils.loadLibrary`, then
     * `DeviceManager.setIDevice`).
     */
    @JvmStatic
    fun create(application: Application): PosTerminal {
        EmvFlowRuntime.init(application)
        return PaxTerminal()
    }
}
