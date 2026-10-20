package com.emvenhance.core.card;

/**
 * Transaction type selected before a transaction starts — carries the ISO 8583-style processing
 * code {@link TransactionConfig#getProcCode()} hands the EMV kernel before app selection
 * (see {@code PaxEmvBehavior#runPreTransProcess}).
 */
public enum TransactionType {

    /** {@code "000000"} — matches PAX's own vendored {@code ETransType.SALE}. */
    SALE("000000", "Sale"),

    /**
     * {@code "010000"} — placeholder. PAX's vendored {@code ETransType} has no Cash In entry to
     * copy from; use this only until a real host spec assigns the actual code.
     */
    CASH_IN("010000", "Cash In"),

    /** {@code "200000"} — matches PAX's own vendored {@code ETransType.REFUND}. */
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
