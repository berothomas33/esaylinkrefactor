package com.emvenhance.core;

/**
 * Vendor SDK adapter for EMV processing.
 *
 * <p>Implementations receive callbacks from the vendor SDK and immediately notify the
 * {@link EmvEngine} — no business logic belongs here. Each POS terminal creates its matching
 * behavior (e.g. {@code PaxTerminal → PaxEmvBehavior}).
 *
 * <pre>
 *   Vendor SDK  →  EmvBehavior  →  EmvEngine.notify*(...)  →  dispatchEmvStep / hooks
 * </pre>
 */
public interface EmvBehavior {

    /**
     * Vendor kernel / parameter initialization before card search.
     *
     * @return true when the kernel is ready for a card
     */
    boolean prepare(TransactionConfig config);

    /**
     * Starts EMV for an already-detected card. Blocking until the kernel finishes or asks
     * to go online (in which case {@link #completeOnline} unblocks the online wait).
     *
     * <p>Every SDK callback must be forwarded to {@code engine.notify*} methods.
     */
    void startEmv(EmvEngine engine, CardPresence card);

    /** Feeds the host authorization result into a blocked online callback. */
    void completeOnline(AuthResult authResult);

    /** Cancels an in-flight EMV process when the vendor SDK supports it. */
    void cancel();
}
