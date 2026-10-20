package com.emvenhance.core.card

/**
 * Transaction type selected before a transaction starts — carries the ISO 8583-style processing
 * code [TransactionConfig.procCode] hands the EMV kernel before app selection
 * (see `PaxEmvBehavior#runPreTransProcess`).
 */
enum class TransactionType(val procCode: String, val label: String) {

    /** `"000000"` — matches PAX's own vendored `ETransType.SALE`. */
    SALE("000000", "Sale"),

    /**
     * `"010000"` — placeholder. PAX's vendored `ETransType` has no Cash In entry to copy from;
     * use this only until a real host spec assigns the actual code.
     */
    CASH_IN("010000", "Cash In"),

    /** `"200000"` — matches PAX's own vendored `ETransType.REFUND`. */
    REFUND("200000", "Refund"),
}
