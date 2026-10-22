package com.emvenhance.vendor.pax

import com.emvenhance.core.host.PrinterBehavior
import com.emvenhance.emvflow.runtime.EmvFlowRuntime
import com.pax.commonlib.utils.LogUtils
import com.pax.dal.entity.EFontTypeAscii
import com.pax.dal.entity.EFontTypeExtCode

/**
 * Real thermal-printer output via [com.pax.dal.IPrinter] (`EmvFlowRuntime.getDal().getPrinter()`).
 * Replaces [com.emvenhance.core.host.HostDefaults.logPrinter], which only ever wrote to Logcat —
 * nothing in this project called the actual PAX printer API before this.
 *
 * [print] blocks while the printer works: `PosTerminal` runs it on its own print thread, one
 * receipt at a time, never on the main thread.
 */
internal class PaxPrinter : PrinterBehavior {

    override suspend fun print(lines: List<String>) {
        val dal = EmvFlowRuntime.getDal() ?: throw IllegalStateException("Neptune DAL not ready — cannot print")
        val printer = dal.printer
        var from = 0
        while (from < lines.size || from == 0) {
            val to = minOf(lines.size, from + LINES_PER_JOB)
            printer.init()
            // 12x24 ASCII font: 32 characters per line on the 384-dot head. The 8x16 font fit 48
            // but its thin strokes were too faint to read.
            printer.fontSet(EFontTypeAscii.FONT_12_24, EFontTypeExtCode.FONT_24_24)
            printer.setGray(PRINT_GRAY)
            for (line in lines.subList(from, to)) {
                printer.printStr(line + "\n", ENCODING)
            }
            if (to >= lines.size) {
                printer.step(FEED_DOTS)
            }
            val ret = printer.start()
            LogUtils.i(TAG, "print start() ret=" + ret + " (lines " + from + "-" + to + " of " + lines.size + ")")
            if (ret != 0) {
                // 2 = out of paper, 8 = overheated, etc. — stop instead of losing lines.
                throw IllegalStateException("Printer error $ret at line $from")
            }
            if (to >= lines.size) {
                break
            }
            from += LINES_PER_JOB
        }
    }

    private companion object {
        const val TAG = "PaxPrinter"

        /**
         * `printStr`'s encoding param — GBK per PAX's own demo/reference usage; it's a superset
         * of ASCII, so plain English/Latin receipt lines print correctly under it too.
         */
        const val ENCODING = "GBK"

        /** Blank feed (in dot-lines) before the tear-off edge, after the last printed line. */
        const val FEED_DOTS = 80

        /**
         * Lines per print job. A full APDU trace runs to hundreds of lines — more than the
         * printer buffer holds in one job — so long output is printed in consecutive jobs.
         */
        const val LINES_PER_JOB = 40

        /**
         * `IPrinter#setGray` darkness: 1 = default, 3 = 150%, 4 = 200% of default. The default
         * came out too faint to read the APDU hex; 4 prints solid black (drop to 3 if the head
         * runs hot on long traces).
         */
        const val PRINT_GRAY = 4
    }
}
