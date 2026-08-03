package com.emvenhance.core;

import io.reactivex.rxjava3.core.Completable;
import java.util.List;

/**
 * Printer behavior: puts receipt lines on paper.
 *
 * <p>Returns a {@link Completable} so the print handler can run the work independently.
 * Print failure is typically non-fatal to the transaction outcome.
 */
public interface PrinterBehavior {

    /**
     * Prints the given lines as a receipt.
     *
     * @param lines ordered receipt content; each element is one print line
     * @return completes on success, errors on failure
     */
    Completable print(List<String> lines);
}
