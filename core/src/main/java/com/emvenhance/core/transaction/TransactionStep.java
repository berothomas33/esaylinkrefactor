package com.emvenhance.core.transaction;

public enum TransactionStep {

    IDLE("Idle"),

    TRANSACTION_STARTED("Transaction started"),

    NEXT_ATTEMPT("Next attempt"),

    WAITING_FOR_CARD("Waiting for card"),

    CARD_DETECTED("Card detected"),

    APPLICATION_SELECTED("Application selected"),

    CARD_READ("Card data read"),

    CARDHOLDER_VERIFIED("Cardholder verified"),

    ONLINE_REQUIRED("Online authorization required"),

    ONLINE_PROCESSING("Processing online authorization"),

    ONLINE_COMPLETED("Online authorization completed"),

    CANCELLING("Cancelling"),

    CANCELLED("Transaction cancelled"),

    APPROVED("Approved"),

    DECLINED("Declined"),

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
}
