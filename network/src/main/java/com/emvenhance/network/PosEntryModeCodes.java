package com.emvenhance.network;

import com.emvenhance.core.card.EntryMethod;

/**
 * ISO 8583 POS entry mode (three digits): how the PAN was read, then the terminal's PIN entry
 * capability ({@code 1}: can accept a PIN). The old app's approved chip sale sent {@code "051"};
 * the other entry methods follow the same layout. Used by {@code SaleCommunicationBehavior}'s
 * sale and refund requests.
 */
public final class PosEntryModeCodes {

    private PosEntryModeCodes() {
    }

    private static final String PIN_ENTRY_CAPABLE = "1";

    public static String forEntryMethod(EntryMethod mode) {
        return panEntryMode(mode) + PIN_ENTRY_CAPABLE;
    }

    private static String panEntryMode(EntryMethod mode) {
        switch (mode) {
            case CHIP:
                return "05";
            case CONTACTLESS:
                return "07";
            case MAGSTRIPE:
                return "90";
            case MANUAL:
                return "01";
            default:
                return "00";
        }
    }
}
