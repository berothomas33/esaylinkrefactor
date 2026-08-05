package com.emvenhance.core.event;

/**
 * High-level transaction lifecycle states, published on the transaction-steps subject.
 *
 * <p>These are the coarse-grained milestones a business analyst would recognize:
 * "transaction started", "card detected", "approved". They move in one direction and
 * each value is emitted at most once per transaction.
 *
 * <p>Contrast with {@link EmvStep}, which tracks the fine-grained kernel phases
 * (SDA/DDA, processing restrictions, risk management) that only EMV engineers care about.
 */
public enum TransactionStep {

    /** No transaction in progress. */
    IDLE("Idle"),

    /** {@code prepare()} called; terminal is initializing. */
    TRANSACTION_STARTED("Transaction started"),

    /** Terminal initialization complete; waiting for a card tap or insert. */
    WAITING_FOR_CARD("Waiting for card"),

    /** Card detected and communication established. */
    CARD_DETECTED("Card detected"),

    /** EMV application selected on the card. */
    APPLICATION_SELECTED("Application selected"),

    /** Card data has been read. PAN and issuer are now known. */
    CARD_READ("Card data read"),

    /** Cardholder identity confirmed (PIN, signature, NoCVM). */
    CARDHOLDER_VERIFIED("Cardholder verified"),

    /** Kernel decided the transaction must go online. */
    ONLINE_REQUIRED("Online authorization required"),

    /** Host communication is in flight. */
    ONLINE_PROCESSING("Processing online authorization"),

    /** Host replied; result available in the event data. */
    ONLINE_COMPLETED("Online authorization completed"),

    /** Transaction approved (online or offline). */
    APPROVED("Approved"),

    /** Transaction declined (online or offline). */
    DECLINED("Declined"),

    /** All post-processing done; transaction is finished. */
    COMPLETED("Transaction completed"),

    /** Unrecoverable error; transaction aborted. */
    ERROR("Error");

    private final String label;

    TransactionStep(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
