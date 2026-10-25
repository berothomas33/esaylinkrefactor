package com.emvenhance.network;

import com.emvenhance.core.card.EntryMethod;

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
