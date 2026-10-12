package com.emvenhance.network;

import com.emvenhance.core.card.EntryMethod;

/**
 * ISO 8583-style POS entry mode codes — same values the old project's {@code SaleRequest}
 * expected. Used by {@link HostRequests} for every request body that carries
 * {@code posEntryMode}.
 */
public final class PosEntryModeCodes {

    private PosEntryModeCodes() {
    }

    public static String forEntryMethod(EntryMethod mode) {
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
