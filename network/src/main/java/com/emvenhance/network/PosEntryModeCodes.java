package com.emvenhance.network;

import com.emvenhance.core.card.EntryMethod;

/**
 * ISO 8583-style POS entry mode codes — same values the old project's {@code PurchaseRequest}/
 * {@code SaleRequest} expected. Shared so {@link RetrofitCommunicationBehavior}'s
 * {@code crypto/purchase} flow and {@code SaleCommunicationBehavior}'s
 * {@code cacore/exchange}+{@code cacore/sale} flow can't drift onto two different tables.
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
