package com.emvenhance.core.param;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Single;
import java.io.File;
import java.util.List;

/**
 * A vendor's EMV parameters, for the EMV params screen: browse what is in the parameter database
 * ({@link #rows}) and, apart, in the kernel's cache ({@link #cachedRows}), download a fresh
 * package from the host, or seed the database with the bundled defaults. The screen knows nothing of the vendor's parameter types: every row is
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
     * The rows of one category as the kernel has them in memory: what the next transaction uses.
     * Asking never loads them, so the screen shows the real state.
     *
     * @return {@code null}: nothing is in memory yet — it loads at the first transaction after a
     *         start, a download or a seed
     * @throws IllegalStateException the memory copy is there but lacks this category (a bug:
     *         the kernel would fail at terminal initialization)
     */
    @Nullable
    List<Row> cachedRows(String category);

    /**
     * Downloads the host's parameter package and applies it to the database; the next
     * transaction loads it into the kernel. Emits what was applied and removed, section by
     * section. Needs the terminal onboarded (an access token). Fails with
     * {@link IllegalStateException} while a transaction is running (nothing written).
     */
    Single<String> download();

    /**
     * Seeds the parameter database with the bundled defaults (debug). Blocking: returns once
     * done.
     *
     * @throws IllegalStateException a transaction is running (nothing written)
     */
    void seedDefaults();

    /** The files of the last download kept for diagnosis (debug builds), by name. */
    List<String> downloadedFiles();

    /** One of {@link #downloadedFiles()}; {@code null} if it isn't there. */
    @Nullable
    File downloadedFile(String name);

    /** One parameter entry: a one-line title, and every field on demand. */
    interface Row {

        String title();

        /** Every field of this entry, from where the row came: the database or the cache. */
        String details();
    }
}
