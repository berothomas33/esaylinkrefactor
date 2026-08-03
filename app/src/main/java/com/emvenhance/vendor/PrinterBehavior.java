package com.emvenhance.vendor;

import io.reactivex.rxjava3.core.Completable;
import java.util.List;

/**
 * Printer behavior: puts receipt lines on paper.
 *
 * <p>Returns {@link Completable} rather than a plain boolean so the orchestrator can
 * compose printing into a reactive chain:
 * <pre>{@code
 *   printer.print(receiptLines)
 *          .subscribeOn(Schedulers.io())
 *          .observeOn(AndroidSchedulers.mainThread())
 *          .subscribe(() -> {}, error -> showPrintError(error));
 * }</pre>
 *
 * <p>The Completable completes normally on success and signals {@code onError} on failure
 * (paper jam, out of paper, hardware fault). The orchestrator decides whether a print
 * failure is fatal to the transaction.
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
