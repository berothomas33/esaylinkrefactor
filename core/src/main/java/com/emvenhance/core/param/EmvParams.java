package com.emvenhance.core.param;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Single;
import java.io.File;
import java.util.List;

/**
 * A vendor's EMV parameters, for the EMV params screen: browse what is in the parameter database
 * and in the kernel's cache, download a fresh package from the host, or seed the database with
 * the bundled defaults. The screen knows nothing of the vendor's parameter types: every row is
 * text the vendor wrote.
 *
 * <p>From {@code PosTerminal#emvParams()}; {@code null} there means the vendor has none to show.
 * Every call except {@link #categories()} may block (database, files): call it off the main thread.
 */
public interface EmvParams {

    /** The categories to browse, in order (e.g. "AID", "CAPK", "PayPass"). */
    List<String> categories();

    /** The rows of one category, from the parameter database. Blocking. */
    List<Row> rows(String category);

    /**
     * Downloads the host's parameter package and applies it to the database; the next
     * transaction loads it into the kernel. Emits what was applied and removed, section by
     * section. Needs the terminal onboarded (an access token).
     */
    Single<String> download();

    /** Seeds the parameter database with the bundled defaults (debug). Blocking. */
    void seedDefaults();

    /** The files of the last download kept for diagnosis (debug builds), by name. */
    List<String> downloadedFiles();

    /** One of {@link #downloadedFiles()}; {@code null} if it isn't there. */
    @Nullable
    File downloadedFile(String name);

    /** One parameter entry: a one-line title, and every field on demand. */
    interface Row {

        String title();

        /**
         * Every field as the kernel gets it: its entry in the kernel's cache (what the next
         * transaction uses), or the database values when it isn't cached yet — and which. Blocking.
         */
        String details();
    }
}
