package com.emvenhance.core.terminal;

import com.emvenhance.core.transaction.TransactionConfig;

/**
 * What {@link PosTerminal} drives on a vendor's EMV kernel — and all it can call.
 *
 * <p>Lifecycle: {@link #run} once per transaction, handed its {@link PosTerminal.Transaction};
 * {@link #cancelCardSearch} when that transaction is cancelled; {@link #dispose} once, when the
 * terminal itself goes away. The EMV engine isn't part of this contract:
 * {@link AbstractEmvBehavior} keeps it ({@code attach} / {@code engine()}). A cancel stops the card
 * search here; the rest of the EMV reads the transaction ({@link AbstractEmvBehavior#isCancelled()})
 * at each step.
 *
 * <p>A vendor doesn't implement this directly: it extends {@link AbstractEmvBehavior}, which
 * implements it and declares the EMV step methods ({@code onSearchCard},
 * {@code onApplicationSelection}, …) every vendor writes. Those are {@code protected} there, not
 * part of this contract, so nothing holding an {@code EmvBehavior} can run a step: steps run only
 * through {@link AbstractEmvBehavior#goToStep}.
 *
 * <p>EMV only — no host / printer logic here.
 */
public interface EmvBehavior {

    /**
     * The whole EMV of one transaction, on the transaction thread: terminal initialization, the
     * card search ({@code SEARCH_CARD}), the EMV on the card found, and again from the search for
     * each retry the kernel asks for (fallback, try again, See Phone, try another interface) —
     * once the kernel has returned. Returns when no retry follows; the outcome went to
     * {@code transaction} ({@code finish*}).
     *
     * @param transaction where the behavior reports its steps (CARD_READ, APPROVED…), and what
     *                    it asks whether a step may still run
     */
    void run(TransactionConfig config, PosTerminal.Transaction transaction);

    /**
     * The transaction was cancelled: stop the card search if one is polling (any thread). The
     * search then reports it cancelled and returns.
     */
    void cancelCardSearch();

    /**
     * Releases what the behavior holds beyond one transaction — {@link AbstractEmvBehavior}'s
     * EmvStep observer. Called by {@link PosTerminal#dispose()}.
     */
    default void dispose() { }
}
