package com.emvenhance.core.event

/**
 * High-level transaction lifecycle states, published on the engine's transaction steps.
 *
 * These are the coarse-grained milestones a business analyst would recognize: "transaction
 * started", "card detected", "approved". They move in one direction and each value is emitted
 * at most once per transaction.
 *
 * Contrast with [EmvStep], which tracks the fine-grained kernel phases (SDA/DDA, processing
 * restrictions, risk management) that only EMV engineers care about.
 */
enum class TransactionStep(val label: String) {

    /** No transaction in progress. */
    IDLE("Idle"),

    /** `prepare()` called; terminal is initializing. */
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

    /**
     * Cancel asked for: the search stops and the EMV winds down. Not the end — CANCELLED
     * follows once the terminal is free (a host call already sent is still waited for).
     */
    CANCELLING("Cancelling"),

    /** Cancelled, and the terminal is free again: the end of a cancelled transaction. */
    CANCELLED("Transaction cancelled"),

    /** Transaction approved (online or offline). */
    APPROVED("Approved"),

    /** Transaction declined (online or offline). */
    DECLINED("Declined"),

    /** All post-processing done; transaction is finished. */
    COMPLETED("Transaction completed"),

    /** Unrecoverable error; transaction aborted. */
    ERROR("Error"),
    ;

    /**
     * Whether this step ends the transaction — COMPLETED, ERROR or CANCELLED, however it got
     * there (approved, declined, failed, cancelled). The one definition of the end: on it the engine
     * stops running and publishing EMV steps, and the behavior stops observing them.
     */
    val isEnd: Boolean get() = this == COMPLETED || this == ERROR || this == CANCELLED
}
