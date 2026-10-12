package com.emvenhance.network;

import androidx.annotation.Nullable;

import com.emvenhance.core.card.EmvTransactionResult;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.network.model.ExchangeRequest;
import com.emvenhance.network.model.RefundRequest;
import com.emvenhance.network.model.SaleExtraData;
import com.emvenhance.network.model.SaleRequest;
import com.google.gson.Gson;

/**
 * The request bodies the host takes, built from what the EMV side attached to
 * {@link TransactionConfig}: its {@link EmvTransactionResult}, field 55 and the online PIN block.
 * One builder per request; a new transaction type adds one here. The card fields the requests
 * share are computed once ({@link CardFields}).
 */
final class HostRequests {

    /**
     * {@code TransactionTypes.SALE.getType()} — confirmed against the old project's real
     * {@code TransactionTypes} enum source (its constants are {@code (id, type)} pairs, and the
     * {@code type} string doesn't always match the Java identifier — {@code SALE(1, "PURCHASE")},
     * whereas e.g. {@code VOID(2, "VOID")} does). The previous guess here, plain {@code "SALE"},
     * looked reasonable (a real captured example confirmed {@code VOID} for
     * {@code VoidInquiryRequest}, and this field has the same shape) but wasn't this one value —
     * the host's {@code cacore.transaction_log.transaction_type} column has a {@code CHECK}
     * constraint that only a real device log caught, rejecting {@code "SALE"} outright.
     */
    static final String TRANSACTION_TYPE_SALE = "PURCHASE";
    /** {@code TransactionTypes.REFUND.getType()} in the old project. */
    static final String TRANSACTION_TYPE_REFUND = "REFUND";

    /**
     * CVM codes — see {@link #cvmCode}. {@link #CVM_ONLINE_PIN} = 2 is confirmed by a real
     * successful old-app sale ({@code "cvm":2} alongside {@code "pinEnterMode":"ONLINE"});
     * {@link #CVM_OFFLINE_PIN} = 1 is inferred from that, not independently confirmed.
     */
    private static final int CVM_NONE = 0;
    private static final int CVM_OFFLINE_PIN = 1;
    private static final int CVM_ONLINE_PIN = 2;

    private static final Gson GSON = new Gson();

    private HostRequests() {
    }

    /** The host's transaction type, sent with the exchange: PURCHASE for a sale, REFUND for a refund. */
    static String transactionType(TransactionType type) {
        return type == TransactionType.REFUND ? TRANSACTION_TYPE_REFUND : TRANSACTION_TYPE_SALE;
    }

    /** {@code orchestration/exchange}'s body: who is paying, how they were verified, for what. */
    static ExchangeRequest exchange(String asyncRequestId, TransactionConfig config,
            EmvTransactionResult emvResult) {
        return new ExchangeRequest(asyncRequestId, emvResult.getPan(), cvmCode(config, emvResult),
                transactionType(config.getType()));
    }

    /** {@code orchestration/sale}'s body. */
    static SaleRequest sale(TransactionConfig config, EmvTransactionResult emvResult) {
        CardFields card = cardFields(emvResult);

        // SaleRequest#getAmount()/getNetAmount() are major-currency-unit doubles (e.g. 10.50),
        // not TransactionConfig#getAmountMinor()'s minor-unit long (cents) — see SaleRequest's
        // javadoc for how that was confirmed. No surcharge/fee concept here, so both are the same
        // value.
        double amountMajor = config.getAmountMinor() / 100.0;

        return new SaleRequest(
                amountMajor,
                amountMajor,
                cvmCode(config, emvResult),
                emvResult.getPan(),
                pinBlockToSend(config),
                config.getIccData(),
                card.expirationMonth,
                card.expirationYear,
                card.trailer,
                PosEntryModeCodes.forEntryMethod(config.getMode()),
                emvResult.getIssuerName(),
                "false",
                extraDataJson(config, emvResult));
    }

    /** {@code orchestration/refund}'s body: the sale's card fields plus the original sale's reference number. */
    static RefundRequest refund(TransactionConfig config, EmvTransactionResult emvResult) {
        CardFields card = cardFields(emvResult);
        return new RefundRequest(
                config.getReferenceNumber() != null ? config.getReferenceNumber().trim() : "",
                config.getAmountMinor() / 100.0,
                cvmCode(config, emvResult),
                emvResult.getPan(),
                pinBlockToSend(config),
                config.getIccData(),
                card.expirationMonth,
                card.expirationYear,
                PosEntryModeCodes.forEntryMethod(config.getMode()),
                emvResult.getIssuerName(),
                card.trailer,
                "false",
                null,
                extraDataJson(config, emvResult));
    }

    /**
     * The old project's real {@code PinEnterMode} enum/indices weren't shared — this derives a CVM
     * code from what {@link EmvTransactionResult}/{@link TransactionConfig} actually carry: an
     * online PIN block means {@link #CVM_ONLINE_PIN}, {@link EmvTransactionResult#isHasPin()} with
     * no online PIN block means an offline (on-card) PIN, otherwise no CVM was performed. See
     * {@link #CVM_ONLINE_PIN} for which values are confirmed.
     */
    static int cvmCode(TransactionConfig config, EmvTransactionResult emvResult) {
        if (pinBlockToSend(config) != null) {
            return CVM_ONLINE_PIN;
        }
        if (emvResult.isHasPin()) {
            return CVM_OFFLINE_PIN;
        }
        return CVM_NONE;
    }

    /**
     * The online PIN block for {@code encPinBlock} (request envelope) and {@code pinBlock}
     * (request body) — only when online PIN was actually entered. Offline PIN (verified by the
     * card), no CVM and signature have no PIN block, so the field is left out of the JSON
     * entirely ({@code null} fields aren't serialized), never sent empty.
     */
    @Nullable
    static String pinBlockToSend(TransactionConfig config) {
        String pinBlock = config.getOnlinePinBlock();
        return pinBlock == null || pinBlock.trim().isEmpty() ? null : pinBlock;
    }

    /**
     * The RSA-wrapped PIN key for the exchange's {@code pinKey} — only alongside a PIN block it
     * encrypts, so never for offline PIN / no CVM / signature.
     */
    @Nullable
    static String pinKeyToSend(TransactionConfig config) {
        return pinBlockToSend(config) != null ? config.getOnlinePinKeyEncrypted() : null;
    }

    static String pinEnterMode(TransactionConfig config, EmvTransactionResult emvResult) {
        switch (cvmCode(config, emvResult)) {
            case CVM_ONLINE_PIN:
                return "ONLINE";
            case CVM_OFFLINE_PIN:
                return "OFFLINE";
            default:
                return "NO_CVM";
        }
    }

    /** The card fields {@link SaleRequest} and {@link RefundRequest} share. */
    private static final class CardFields {
        int expirationMonth;
        int expirationYear;
        String trailer = "";
    }

    private static CardFields cardFields(EmvTransactionResult emvResult) {
        CardFields fields = new CardFields();
        String expDate = emvResult.getExpDate();
        if (expDate != null && expDate.length() >= 4) {
            try {
                fields.expirationYear = Integer.parseInt(expDate.substring(0, 2));
                fields.expirationMonth = Integer.parseInt(expDate.substring(2, 4));
            } catch (NumberFormatException ignored) {
                // Leave both at 0 — matches the old project's getSaleRequest(), which also
                // swallows a malformed expiry rather than failing the whole request.
            }
        }

        String pan = emvResult.getPan();
        String track2 = emvResult.getTrack2();
        if (track2 != null && pan != null && expDate != null) {
            String prefix = pan + "D" + expDate;
            if (track2.length() > prefix.length() && track2.startsWith(prefix)) {
                fields.trailer = track2.substring(prefix.length());
            }
        }
        return fields;
    }

    private static String extraDataJson(TransactionConfig config, EmvTransactionResult emvResult) {
        SaleExtraData extraData = new SaleExtraData(
                emvResult.getAid() != null ? emvResult.getAid() : "",
                emvResult.getEmvAppName() != null ? emvResult.getEmvAppName() : "",
                emvResult.getIssuerName(),
                pinEnterMode(config, emvResult),
                emvResult.getCardHolderName(),
                cardTypeLabel(config.getMode()));
        return GSON.toJson(extraData);
    }

    /**
     * {@code extraData.cardType}. {@code "CHIP"} is confirmed by a real successful old-app chip
     * sale; the contactless/swipe/manual labels are still unconfirmed guesses.
     */
    private static String cardTypeLabel(EntryMethod mode) {
        switch (mode) {
            case CHIP:
                return "CHIP";
            case CONTACTLESS:
                return "CLSS";
            case MAGSTRIPE:
                return "SWIPE";
            case MANUAL:
                return "MANUAL";
            default:
                return "CHIP";
        }
    }
}
