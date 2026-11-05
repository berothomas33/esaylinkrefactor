package com.emvenhance.core.transaction;

public enum TransactionStep {

    IDLE("Idle"),

    TRANSACTION_STARTED("Transaction started"),

    WAITING_FOR_CARD("Waiting for card"),

    CARD_DETECTED("Card detected"),

    APPLICATION_SELECTED("Application selected"),

    CARD_READ("Card data read"),

    CARDHOLDER_VERIFIED("Cardholder verified"),

    ONLINE_REQUIRED("Online authorization required"),

    ONLINE_PROCESSING("Processing online authorization"),

    ONLINE_COMPLETED("Online authorization completed"),

    /** Mastercard Single Tap: the bank asks for the PIN; the same sale goes again with it. */
    SINGLE_TAP_PIN_REQUIRED("The bank asks for the PIN"),

    CANCELLING("Cancelling"),

    CANCELLED("Transaction cancelled"),

    APPROVED("Approved"),

    DECLINED("Declined"),

    /** The transaction failed; ERROR, with the same message, is delivered as its end. */
    FAILED("Failed"),

    ENDING("Ending the transaction"),

    ACKNOWLEDGING("Acknowledging the sale with the host"),

    REVERSING("Reversing the request at the host"),

    CLOSING_COMPLETED("Acknowledgement / reversal completed"),

    COMPLETED("Transaction completed"),

    ERROR("Error");

    private final String label;

    TransactionStep(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isEnd() {
        return this == COMPLETED || this == ERROR || this == CANCELLED;
    }

    /**
     * The step that decides how a transaction ends — the first one delivered is its state:
     * nothing after it runs. Its end is delivered last, after the closings at the host.
     */
    public boolean isDecision() {
        return this == CANCELLING || this == APPROVED || this == DECLINED || this == FAILED;
    }

    /**
     * The steps the transaction itself delivers after its decision: ENDING, the closings at the
     * host and the end. Nothing else gets in once a transaction is decided.
     */
    public boolean isAfterDecision() {
        return this == ENDING || this == ACKNOWLEDGING || this == REVERSING
                || this == CLOSING_COMPLETED || isEnd();
    }

    /** The end a decision leads to: CANCELLED, COMPLETED or ERROR. */
    public TransactionStep endOfDecision() {
        switch (this) {
            case CANCELLING:
                return CANCELLED;
            case APPROVED:
            case DECLINED:
                return COMPLETED;
            default:
                return ERROR;
        }
    }
}
