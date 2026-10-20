package com.emvenhance.core.host

/**
 * Receipt printer port. Owned by [com.emvenhance.core.terminal.PosTerminal], which calls it on
 * its own print thread, one receipt at a time.
 */
fun interface PrinterBehavior {

    suspend fun print(lines: List<String>)
}
