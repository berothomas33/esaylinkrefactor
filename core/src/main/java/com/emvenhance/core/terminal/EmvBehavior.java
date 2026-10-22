package com.emvenhance.core.terminal;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;

/**
 * What {@link PosTerminal} drives on a vendor's EMV kernel — and all it can call.
 *
 * <p>Lifecycle: {@link #attach} once, when the terminal is built (its {@link EmvEngine});
 * {@link #prepare} → (PosTerminal search) → {@link #start} once per attempt, each handed the
 * attempt's {@link PosTerminal.Transaction}; {@link #dispose} once, when the terminal itself goes
 * away. A cancel isn't a call into the behavior: it reads that transaction
 * ({@link AbstractEmvBehavior#isCancelled()}) at each step.
 *
 * <p>A vendor doesn't implement this directly: it extends {@link AbstractEmvBehavior}, which
 * implements it and declares the EMV step methods ({@code onApplicationSelection}, …) every
 * vendor writes. Those are {@code protected} there, not part of this contract, so nothing
 * holding an {@code EmvBehavior} can run a step: steps run only through
 * {@link AbstractEmvBehavior#goToStep}. Tests stub this interface to drive the terminal with no
 * EMV behind it.
 *
 * <p>EMV only — no host / printer logic here.
 */
public interface EmvBehavior {

    /** The terminal's engine, given once by {@link PosTerminal}'s constructor; it never changes. */
    void attach(EmvEngine engine);

    /**
     * Terminal initialization for one attempt.
     *
     * @param transaction where the behavior reports its steps (CARD_READ, APPROVED…), and what
     *                    it asks whether a step may still run
     * @return {@code false} if it failed or the transaction ended meanwhile
     */
    boolean prepare(TransactionConfig config, PosTerminal.Transaction transaction);

    /** EMV on the card the search found, for the same attempt as {@link #prepare}. */
    void start(TransactionConfig config, CardPresence card, PosTerminal.Transaction transaction);

    /**
     * Releases what the behavior holds beyond one transaction — {@link AbstractEmvBehavior}'s
     * EmvStep observer. Called by {@link PosTerminal#dispose()}.
     */
    default void dispose() { }
}
