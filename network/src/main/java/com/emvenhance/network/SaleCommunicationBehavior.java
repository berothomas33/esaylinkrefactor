package com.emvenhance.network;

import android.content.Context;

import com.emvenhance.core.card.EmvTransactionResult;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.network.crypto.AesEnvelopeCrypto;
import com.emvenhance.network.crypto.RsaPublicKeyEncryptor;
import com.emvenhance.network.model.ExchangeRequest;
import com.emvenhance.network.model.ExchangeResponse;
import com.emvenhance.network.model.GeneralRequest;
import com.emvenhance.network.model.GeneralResponse;
import com.emvenhance.network.model.SaleExtraData;
import com.emvenhance.network.model.SaleRequest;
import com.emvenhance.network.model.SaleResponse;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.network.tlv.IssuerResponseFields;
import com.google.gson.Gson;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;

import io.reactivex.rxjava3.core.Single;

/**
 * Real {@link CommunicationBehavior} for the encrypted-envelope sale flow —
 * {@code orchestration/exchange} (establishes a fresh per-transaction TEK, and forwards the PEK if
 * online PIN was collected) then {@code orchestration/sale} (the actual authorization), replacing
 * the old project's {@code SaleActivity#callExchangeProcess}/{@code #callSaleProcess} pair. (Both
 * paths were first guessed as {@code cacore/exchange}/{@code cacore/sale} from
 * {@code SaleActivity}'s field names — wrong, and confirmed wrong by a real 400 Bad Request in
 * testing; {@code HostApiConnection}'s real {@code EmvApiConnection} source, found afterward,
 * has the correct paths — {@code cacore/sale} does exist, but as a separate, simpler test
 * endpoint.)
 *
 * <p>Deliberately narrower than the old project's {@code SaleActivity}, per the EMV-flow split
 * this codebase already made ({@code EmvEngine} + a vendor {@code EmvBehavior} own card reading;
 * this class only owns going online once {@link TransactionConfig#getEmvResult()} is populated):
 * <ul>
 *   <li>{@code new MomknPayScenario().sale(...)} (EMV/card-reading via the old vendor SDK) has no
 *       counterpart here — {@link TransactionConfig#getEmvResult()} is expected to already be
 *       populated by the vendor {@code EmvBehavior} before {@link #authorize} is ever called; see
 *       {@code PaxEmvBehavior#startOnlineProcess}.
 *   <li>Offline pending-transaction bookkeeping ({@code OfflineViewModel#addTransaction}/
 *       {@code #deleteTransaction}), the Mastercard-CLSS single-tap EasyLink retry, receipt
 *       printing, the reverse-on-failure call, the acknowledgement call, and the PENDING-status
 *       retry loop are all out of scope — a PENDING or failed envelope surfaces as a plain
 *       {@link Single#error}, once, with no retry.
 * </ul>
 *
 * <p>{@code GeneralRequest}/{@code ExchangeRequest}/{@code SaleRequest}/their response
 * counterparts are now confirmed against the real classes (found in an uploaded
 * {@code model_layer.rar}) — see each model class's own javadoc for exactly what changed from the
 * first, setter-call-reconstructed version. Headers come from {@link HostHeaders#buildAuthenticated}
 * — post-onboarding, bearer-token mode, using {@link #sn} (this terminal's own serial number,
 * passed in by {@code PaxTerminal}, which has PAX's {@code ModelInfo}; this module stays
 * vendor-agnostic, so it can't fetch that itself) and {@code onboardingState.getAccessToken()}.
 * Only a captured {@code tmsFileDownload} call confirmed that mode is what a real authenticated
 * request looks like — {@code orchestration/exchange}/{@code orchestration/sale} using the same
 * mode is a reasonable inference, not independently confirmed.
 *
 * <p>The old project supplied a {@code paymentAsyncID} from whatever aggregator launched
 * {@code SaleActivity} — this codebase has no such caller, so a fresh {@link UUID} is generated
 * per {@link #authorize} call and reused across both the exchange and sale requests instead.
 */
public final class SaleCommunicationBehavior implements CommunicationBehavior {

    private static final int SUCCESS_STATUS_CODE = 200;
    private static final int TEK_LENGTH_BYTES = 16;
    /** The old project's real {@code TransactionTypes.SALE.getType()} value isn't confirmed. */
    private static final String TRANSACTION_TYPE_SALE = "SALE";

    /** CVM codes — see {@link #cvmCode}; the old project's real {@code PinEnterMode} indices aren't confirmed. */
    private static final int CVM_NONE = 0;
    private static final int CVM_ONLINE_PIN = 1;
    private static final int CVM_OFFLINE_PIN = 2;

    private final HostApiConnection connection;
    private final OnboardingState onboardingState;
    private final String sn;
    private final Gson gson = new Gson();

    public SaleCommunicationBehavior(Context context, String sn) {
        this(HostApiClient.create(), new OnboardingState(context), sn);
    }

    public SaleCommunicationBehavior(HostApiConnection connection, OnboardingState onboardingState, String sn) {
        this.connection = connection;
        this.onboardingState = onboardingState;
        this.sn = sn;
    }

    @Override
    public Single<AuthResult> authorize(TransactionConfig config) {
        EmvTransactionResult emvResult = config.getEmvResult();
        if (emvResult == null) {
            return Single.error(new SaleException(
                    "No EmvTransactionResult on TransactionConfig — the vendor EmvBehavior must "
                            + "attach one (see TransactionConfig#withEmvResult) before going online"));
        }
        String hostPublicKey = onboardingState.getPublicKey();
        if (hostPublicKey == null) {
            return Single.error(new SaleException(
                    "No host public key on file — onboarding must complete before a TEK can be wrapped for the host"));
        }
        String accessToken = onboardingState.getAccessToken();
        if (accessToken == null) {
            return Single.error(new SaleException(
                    "No access token on file — onboarding must complete before a sale can go online"));
        }
        Map<String, String> headers = HostHeaders.buildAuthenticated(sn, accessToken);

        byte[] tek = new byte[TEK_LENGTH_BYTES];
        new SecureRandom().nextBytes(tek);
        String transactionKeyEncrypted;
        try {
            transactionKeyEncrypted = RsaPublicKeyEncryptor.encryptToBase64(hostPublicKey, tek);
        } catch (GeneralSecurityException e) {
            return Single.error(new SaleException("Failed to RSA-encrypt the transaction key (TEK)", e));
        }

        String asyncRequestId = UUID.randomUUID().toString();

        return exchange(headers, config, emvResult, tek, transactionKeyEncrypted, asyncRequestId)
                .flatMap(exchangeResponse -> sale(headers, config, emvResult, tek, asyncRequestId))
                .map(SaleCommunicationBehavior::toAuthResult);
    }

    private Single<ExchangeResponse> exchange(Map<String, String> headers, TransactionConfig config,
            EmvTransactionResult emvResult, byte[] tek, String transactionKeyEncrypted, String asyncRequestId) {
        int cvm = cvmCode(config, emvResult);
        ExchangeRequest exchangeRequest = new ExchangeRequest(
                asyncRequestId, emvResult.getPan(), cvm, TRANSACTION_TYPE_SALE);

        String encSerializedRequest;
        try {
            encSerializedRequest = AesEnvelopeCrypto.encrypt(tek, gson.toJson(exchangeRequest));
        } catch (GeneralSecurityException e) {
            return Single.error(new SaleException("Failed to AES-encrypt the exchange request", e));
        }

        GeneralRequest request = GeneralRequest.forExchange(encSerializedRequest, asyncRequestId,
                transactionKeyEncrypted, config.getOnlinePinKeyEncrypted(), TRANSACTION_TYPE_SALE);

        return connection.exchange(headers, request)
                .flatMap(response -> decryptEnvelope(response, tek, "Exchange", ExchangeResponse.class));
    }

    private Single<SaleResponse> sale(Map<String, String> headers, TransactionConfig config,
            EmvTransactionResult emvResult, byte[] tek, String asyncRequestId) {
        SaleRequest saleRequest = buildSaleRequest(config, emvResult);

        String encSerializedRequest;
        try {
            encSerializedRequest = AesEnvelopeCrypto.encrypt(tek, gson.toJson(saleRequest));
        } catch (GeneralSecurityException e) {
            return Single.error(new SaleException("Failed to AES-encrypt the sale request", e));
        }

        GeneralRequest request = GeneralRequest.forSale(
                encSerializedRequest, asyncRequestId, config.getOnlinePinBlock());

        return connection.sale(headers, request)
                .flatMap(response -> decryptEnvelope(response, tek, "Sale", SaleResponse.class));
    }

    private <T> Single<T> decryptEnvelope(GeneralResponse response, byte[] tek, String step, Class<T> type) {
        if (response.getStatusCode() != SUCCESS_STATUS_CODE || response.getData() == null
                || response.getData().getEncSerializedResponse() == null) {
            String message = response.getMessage() != null ? response.getMessage() : step + " failed";
            return Single.error(new SaleException(step + " failed (" + response.getStatusCode() + "): " + message));
        }
        try {
            String decrypted = AesEnvelopeCrypto.decrypt(tek, response.getData().getEncSerializedResponse());
            return Single.just(gson.fromJson(decrypted, type));
        } catch (GeneralSecurityException e) {
            return Single.error(new SaleException("Failed to decrypt the " + step + " response", e));
        }
    }

    private SaleRequest buildSaleRequest(TransactionConfig config, EmvTransactionResult emvResult) {
        int expirationMonth = 0;
        int expirationYear = 0;
        String expDate = emvResult.getExpDate();
        if (expDate != null && expDate.length() >= 4) {
            try {
                expirationYear = Integer.parseInt(expDate.substring(0, 2));
                expirationMonth = Integer.parseInt(expDate.substring(2, 4));
            } catch (NumberFormatException ignored) {
                // Leave both at 0 — matches the old project's getSaleRequest(), which also
                // swallows a malformed expiry rather than failing the whole request.
            }
        }

        String trailer = "";
        String pan = emvResult.getPan();
        String track2 = emvResult.getTrack2();
        if (track2 != null && pan != null && expDate != null) {
            String prefix = pan + "D" + expDate;
            if (track2.length() > prefix.length() && track2.startsWith(prefix)) {
                trailer = track2.substring(prefix.length());
            }
        }

        SaleExtraData extraData = new SaleExtraData(
                emvResult.getAid() != null ? emvResult.getAid() : "",
                emvResult.getEmvAppName() != null ? emvResult.getEmvAppName() : "",
                emvResult.getIssuerName(),
                pinEnterMode(config, emvResult),
                emvResult.getCardHolderName(),
                cardTypeLabel(config.getMode()));

        // SaleRequest#getAmount()/getNetAmount() are major-currency-unit doubles (e.g. 10.50),
        // not TransactionConfig#getAmountMinor()'s minor-unit long (cents) — see SaleRequest's
        // javadoc for how that was confirmed. No surcharge/fee concept here, so both are the same
        // value.
        double amountMajor = config.getAmountMinor() / 100.0;

        return new SaleRequest(
                amountMajor,
                amountMajor,
                cvmCode(config, emvResult),
                pan,
                config.getOnlinePinBlock(),
                config.getIccData(),
                expirationMonth,
                expirationYear,
                trailer,
                PosEntryModeCodes.forEntryMethod(config.getMode()),
                emvResult.getIssuerName(),
                "false",
                gson.toJson(extraData));
    }

    /**
     * The old project's real {@code PinEnterMode} enum/indices weren't shared — this derives a CVM
     * code from what {@link EmvTransactionResult}/{@link TransactionConfig} actually carry: an
     * online PIN block means {@link #CVM_ONLINE_PIN}, {@link EmvTransactionResult#isHasPin()} with
     * no online PIN block means an offline (on-card) PIN, otherwise no CVM was performed. Confirm
     * the real code values against the server team before relying on this for a live transaction.
     */
    private static int cvmCode(TransactionConfig config, EmvTransactionResult emvResult) {
        if (config.getOnlinePinBlock() != null) {
            return CVM_ONLINE_PIN;
        }
        if (emvResult.isHasPin()) {
            return CVM_OFFLINE_PIN;
        }
        return CVM_NONE;
    }

    private static String pinEnterMode(TransactionConfig config, EmvTransactionResult emvResult) {
        switch (cvmCode(config, emvResult)) {
            case CVM_ONLINE_PIN:
                return "ONLINE_PIN";
            case CVM_OFFLINE_PIN:
                return "OFFLINE_PIN";
            default:
                return "NO_CVM";
        }
    }

    /** Matches the {@code CardType.INSERT}/{@code CLSS} constants referenced (not defined) in the old project. */
    private static String cardTypeLabel(EntryMethod mode) {
        switch (mode) {
            case CHIP:
                return "INSERT";
            case CONTACTLESS:
                return "CLSS";
            case MAGSTRIPE:
                return "SWIPE";
            case MANUAL:
                return "MANUAL";
            default:
                return "INSERT";
        }
    }

    private static AuthResult toAuthResult(SaleResponse response) {
        if (response.getResponseCode() != 0) {
            String message = response.getResponseMessage() != null ? response.getResponseMessage() : "Declined";
            return AuthResult.declined(String.valueOf(response.getResponseCode()), message);
        }
        String authCode = response.getAuthCode() != null ? response.getAuthCode() : "";
        byte[] arpc = IssuerResponseFields.arpc(response.getChipData());
        byte[] issuerScript = IssuerResponseFields.issuerScript(response.getChipData());
        return AuthResult.approved(authCode, "00", arpc, issuerScript);
    }
}
