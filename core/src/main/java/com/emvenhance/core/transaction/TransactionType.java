package com.emvenhance.core.transaction;

public enum TransactionType {

    SALE("000000", "Sale"),

    CASH_IN("010000", "Cash In"),

    REFUND("200000", "Refund");

    private final String procCode;
    private final String label;

    TransactionType(String procCode, String label) {
        this.procCode = procCode;
        this.label = label;
    }

    public String getProcCode() {
        return procCode;
    }

    public String getLabel() {
        return label;
    }
}
