package com.emvenhance.core.param

import java.io.File

/**
 * A vendor's EMV parameters, for the EMV params screen: browse what is in the parameter database
 * and in the kernel's cache, download a fresh package from the host, or seed the database with
 * the bundled defaults. The screen knows nothing of the vendor's parameter types: every row is
 * text the vendor wrote.
 *
 * From `PosTerminal.emvParams()`; `null` there means the vendor has none to show. Every call
 * except [categories] and [download] may block (database, files): call it off the main thread.
 * [download] is main-safe: it moves its own blocking work off the caller's thread.
 */
interface EmvParams {

    /** The categories to browse, in order (e.g. "AID", "CAPK", "PayPass"). */
    fun categories(): List<String>

    /** The rows of one category, from the parameter database. Blocking. */
    fun rows(category: String): List<Row>

    /**
     * Downloads the host's parameter package and applies it to the database; the next
     * transaction loads it into the kernel. Returns what was applied and removed, section by
     * section. Needs the terminal onboarded (an access token).
     */
    suspend fun download(): String

    /** Seeds the parameter database with the bundled defaults (debug). Blocking. */
    fun seedDefaults()

    /** The files of the last download kept for diagnosis (debug builds), by name. */
    fun downloadedFiles(): List<String>

    /** One of [downloadedFiles]; `null` if it isn't there. */
    fun downloadedFile(name: String): File?

    /** One parameter entry: a one-line title, and every field on demand. */
    interface Row {

        fun title(): String

        /**
         * Every field as the kernel gets it: its entry in the kernel's cache (what the next
         * transaction uses), or the database values when it isn't cached yet — and which.
         * Blocking.
         */
        fun details(): String
    }
}
