package com.emvenhance.core.event;

public enum EmvStep {

    TERMINAL_INITIALIZATION(0, "Terminal initialization (preTransProcess)"),

    SEARCH_CARD(1, "Search card"),

    APPLICATION_SELECTION(2, "Application selection"),
    WAIT_APPLICATION_SELECTION(3, "Wait application selection"),
    FINAL_APPLICATION_SELECTION(4, "Final application selection"),

    READ_APPLICATION_DATA(5, "Read application data"),
    SET_TRANSACTION_DATA(6, "Set transaction TLVs"),

    OFFLINE_DATA_AUTHENTICATION(7, "Offline data authentication (SDA/DDA/CDA)"),
    PROCESS_RESTRICTIONS(8, "Processing restrictions"),

    CARDHOLDER_VERIFICATION(9, "Cardholder verification (CVM)"),
    OFFLINE_PIN_VERIFICATION(10, "Offline PIN verification"),

    TERMINAL_RISK_MANAGEMENT(11, "Terminal risk management"),
    TERMINAL_ACTION_ANALYSIS(12, "Terminal action analysis"),

    START_ONLINE_PROCESS(13, "Start online processing"),

    ISSUER_AUTHENTICATION(14, "Issuer authentication"),
    SCRIPT_PROCESSING(15, "Issuer script processing"),
    TRANSACTION_COMPLETION(16, "Transaction completion");

    private final int number;
    private final String title;

    EmvStep(int number, String title) {
        this.number = number;
        this.title = title;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }
}
