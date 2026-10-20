package com.emvenhance.vendor

import android.app.Application
import com.emvenhance.core.terminal.PosTerminal
import com.emvenhance.vendor.ingenico.IngenicoTerminal

/**
 * Ingenico flavor's factory — the only place in this flavor that knows [IngenicoTerminal]'s
 * name.
 *
 * UI and core never import vendor types — only [PosTerminal].
 */
object TerminalFactory {

    @JvmStatic
    fun create(application: Application): PosTerminal = IngenicoTerminal()
}
