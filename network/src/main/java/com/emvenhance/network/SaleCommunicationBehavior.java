package com.emvenhance.network;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;

import com.emvenhance.core.card.EmvTransactionResult;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.HostNotReachedException;
import com.emvenhance.network.crypto.AesEnvelopeCrypto;
import com.emvenhance.network.crypto.RsaPublicKeyEncryptor;
import com.emvenhance.network.model.AcknowledgementRequest;
import com.emvenhance.network.model.ExchangeRequest;
import com.emvenhance.network.model.ExchangeResponse;
import com.emvenhance.network.model.GeneralRequest;
import com.emvenhance.network.model.GeneralResponse;
import com.emvenhance.network.model.RefundRequest;
import com.emvenhance.network.model.ReverseRequest;
import com.emvenhance.network.model.SaleExtraData;
import com.emvenhance.network.model.SaleRequest;
import com.emvenhance.network.model.SaleResponse;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.network.tlv.IssuerResponseFields;
import com.google.gson.Gson;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import retrofit2.HttpException;

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
 *       printing, and the PENDING-status retry loop are all out of scope — a PENDING or failed envelope surfaces as a plain
 *       {@link Single#error}, once, with no retry.
 * </ul>
 *
 * <p>{@code GeneralRequest}/{@code ExchangeRequest}/{@code SaleRequest}/their response
 * counterparts are now confirmed against the real classes (found in an uploaded
 * {@code model_layer.rar}) — see each model class's own javadoc for exactly what changed from the
 * first, setter-call-reconstructed version. Headers come from {@link HostHeaders#buildAuthenticated}
 * — post-onboarding, bearer-token mode, using {@link #sn} (this terminal's own serial number,
 * passed in by {@code PaxTerminal}, which has PAX's {@code ModelInfo}; this module stays
 * vendor-agnostic, so it can't fetch that itself) and a token from
 * {@link OnboardingClient#ensureValidAccessToken} (refreshed first if near/past expiry, rather
 * than the raw {@code onboardingState.getAccessToken()} this used to read directly — that path
 * reused whatever token onboarding first minted forever, which is exactly how a real terminal
 * started getting {@code "Jwt expired"} from the host). Only a captured {@code tmsFileDownload}
 * call confirmed that mode is what a real authenticated request looks like —
 * {@code orchestration/exchange}/{@code orchestration/sale} using the same mode is a reasonable
 * inference, not independently confirmed.
 *
 * <p>The old project supplied a {@code paymentAsyncID} from whatever aggregator launched
 * {@code SaleActivity} — this codebase has no such caller, so {@code PosTerminal#authorize} names
 * each request ({@link TransactionConfig#getAsyncRequestId()}) and both the exchange and the
 * sale/refund go with that name ({@link UUID a fresh one} if it has none); {@link #acknowledge}
 * and {@link #reverse} close the request by the same name once the transaction has ended
 * ({@code orchestration/acknowledgement} / {@code orchestration/reverse}).
 */
public final class SaleCommunicationBehavior implements CommunicationBehavior {

    private static final int SUCCESS_STATUS_CODE = 200;
    private static final int TEK_LENGTH_BYTES = 16;
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
    private static final String TRANSACTION_TYPE_SALE = "PURCHASE";
    /** {@code TransactionTypes.REFUND.getType()} in the old project. */
    private static final String TRANSACTION_TYPE_REFUND = "REFUND";

    private static final String TAG = "SaleComm";
    /** Logcat truncates a single line at ~4000 characters. */
    private static final int LOG_CHUNK = 3500;

    /**
     * CVM codes — see {@link #cvmCode}. Both PIN codes are confirmed by real successful old-app
     * sales: {@code "cvm":2} with {@code "pinEnterMode":"ONLINE"}, and {@code "cvm":3} with
     * {@code "pinEnterMode":"OFFLINE"} (Mastercard chip, offline PIN). {@link #CVM_NONE} = 0 is
     * not confirmed.
     */
    private static final int CVM_NONE = 0;
    private static final int CVM_ONLINE_PIN = 2;
    private static final int CVM_OFFLINE_PIN = 3;

    /** {@code netAmount}: the old app's approved sale sent {@code 0.0} next to its amount. */
    private static final double NET_AMOUNT = 0.0;
    private static final String MASTERCARD_RID = "A000000004";

    private final HostApiConnection connection;
    private final OnboardingClient onboarding;
    private final OnboardingState onboardingState;
    private final HostSettings hostSettings;
    private final String sn;
    private final Gson gson = new Gson();

    public SaleCommunicationBehavior(Context context, String sn) {
        this(HostApiClient.create(), new OnboardingClient(), new OnboardingState(context),
                new HostSettings(context), sn);
    }

    public SaleCommunicationBehavior(HostApiConnection connection, OnboardingClient onboarding,
            OnboardingState onboardingState, HostSettings hostSettings, String sn) {
        this.connection = connection;
        this.onboarding = onboarding;
        this.onboardingState = onboardingState;
        this.hostSettings = hostSettings;
        this.sn = sn;
    }

    /**
     * Sends the transaction to the host by its type: SALE → {@code orchestration/sale}, REFUND →
     * {@code orchestration/refund}, each after its own {@code orchestration/exchange}. Anything
     * that can't go online — a type with no host request yet, a refund without the original
     * reference number — is {@link AuthResult#notSent online failed}: this terminal approves
     * nothing offline, and a type is never sent as another (a refund sent as a PURCHASE would
     * charge the card).
     *
     * <p>Each error names its step ({@code "Exchange: HTTP 403 Forbidden"}), and says whether
     * the host could have approved the sale: a {@link HostNotReachedException} when it certainly
     * couldn't (see {@link #classify}), which {@code PosTerminal} doesn't reverse.
     */
    @Override
    public Single<AuthResult> authorize(TransactionConfig config) {
        String notSent = whyNotSent(config);
        if (notSent != null) {
            Log.w(TAG, notSent + " — not sent to the host");
            return Single.just(AuthResult.notSent(notSent));
        }
        EmvTransactionResult emvResult = config.getEmvResult();
        if (emvResult == null) {
            return Single.error(new HostNotReachedException(
                    "No EmvTransactionResult on TransactionConfig — the vendor EmvBehavior must "
                            + "attach one (see TransactionConfig#withEmvResult) before going online"));
        }
        String hostPublicKey = onboardingState.getPublicKey();
        if (hostPublicKey == null) {
            return Single.error(new HostNotReachedException(
                    "No host public key on file — onboarding must complete before a TEK can be wrapped for the host"));
        }
        if (onboardingState.getAccessToken() == null) {
            return Single.error(new HostNotReachedException(
                    "No access token on file — onboarding must complete before a sale can go online"));
        }

        return onboarding.ensureValidAccessToken(HostHeaders.build(sn), onboardingState)
                .onErrorResumeNext(e -> Single.error(notReached("Token refresh", e)))
                .flatMap(accessToken -> goOnline(HostHeaders.buildAuthenticated(sn, accessToken),
                        config, emvResult, hostPublicKey));
    }

    /**
     * Why {@link #authorize} never sends {@code config} to the host — a type with no host request
     * yet, a refund without the original reference number — or {@code null}: it goes online.
     */
    @Nullable
    private static String whyNotSent(TransactionConfig config) {
        TransactionType type = config.getType();
        if (type == TransactionType.REFUND) {
            String reference = config.getReferenceNumber();
            if (reference == null || reference.trim().isEmpty()) {
                return "Refund needs the original sale's reference number";
            }
            return null;
        }
        if (type != TransactionType.SALE) {
            // CASH_IN (POS-as-ATM deposit: atm/exchange + atm/deposit) has no request body yet —
            // the old app's screen that built it wasn't shared.
            return type.getLabel() + " isn't supported online yet";
        }
        return null;
    }

    /**
     * {@code orchestration/acknowledgement}: the sale or refund {@code sent} names is complete on
     * the terminal. Plain JSON with the sale's headers; any status but 200 is an error.
     */
    @Override
    public Completable acknowledge(TransactionConfig sent) {
        return closeOnline(sent, "Acknowledgement", (headers, id, type) -> connection
                .acknowledgement(headers, new AcknowledgementRequest(id, type))
                .flatMapCompletable(response ->
                        checkStatus("Acknowledgement", response.getStatusCode(), response.getMessage())));
    }

    /**
     * {@code orchestration/reverse}: undo the sale or refund {@code sent} names. Plain JSON with
     * the sale's headers; any status but 200 is an error.
     */
    @Override
    public Completable reverse(TransactionConfig sent) {
        return closeOnline(sent, "Reversal", (headers, id, type) -> connection
                .reverse(headers, new ReverseRequest(id, type))
                .flatMapCompletable(response ->
                        checkStatus("Reversal", response.getStatusCode(), response.getMessage())));
    }

    /** One follow-up call: its headers, the request's name, its type as the exchange sent it. */
    interface FollowUpCall {
        Completable send(Map<String, String> headers, String asyncRequestId, String transactionType);
    }

    /**
     * Sends a follow-up for a request {@link #authorize} sent — nothing for one it never sent
     * ({@link #whyNotSent}) or that has no name. Headers as {@link #sale}'s: a valid token, then
     * {@code mToken}.
     */
    private Completable closeOnline(TransactionConfig sent, String what, FollowUpCall call) {
        String asyncRequestId = sent.getAsyncRequestId();
        if (asyncRequestId == null || whyNotSent(sent) != null) {
            Log.i(TAG, what + " not sent: the request never went to the host");
            return Completable.complete();
        }
        String transactionType = sent.getType() == TransactionType.REFUND
                ? TRANSACTION_TYPE_REFUND : TRANSACTION_TYPE_SALE;
        return onboarding.ensureValidAccessToken(HostHeaders.build(sn), onboardingState)
                .flatMapCompletable(accessToken -> {
                    Map<String, String> headers =
                            new HashMap<>(HostHeaders.buildAuthenticated(sn, accessToken));
                    headers.put("mToken", hostSettings.getMToken());
                    return call.send(headers, asyncRequestId, transactionType);
                });
    }

    static Completable checkStatus(String what, int statusCode, @Nullable String message) {
        if (statusCode == SUCCESS_STATUS_CODE) {
            return Completable.complete();
        }
        return Completable.error(new SaleException(what + " failed (" + statusCode + "): "
                + (message != null ? message : "no message")));
    }

    private Single<AuthResult> goOnline(Map<String, String> headers, TransactionConfig config,
            EmvTransactionResult emvResult, String hostPublicKey) {
        byte[] tek = new byte[TEK_LENGTH_BYTES];
        new SecureRandom().nextBytes(tek);
        String transactionKeyEncrypted;
        try {
            transactionKeyEncrypted = RsaPublicKeyEncryptor.encryptToHex(hostPublicKey, tek);
        } catch (GeneralSecurityException e) {
            return Single.error(new HostNotReachedException("Failed to RSA-encrypt the transaction key (TEK)", e));
        }

        String asyncRequestId = config.getAsyncRequestId() != null
                ? config.getAsyncRequestId() : UUID.randomUUID().toString();

        boolean refund = config.getType() == TransactionType.REFUND;
        String exchangeType = refund ? TRANSACTION_TYPE_REFUND : TRANSACTION_TYPE_SALE;
        return exchange(headers, config, emvResult, tek, transactionKeyEncrypted, asyncRequestId, exchangeType)
                .flatMap(exchangeResponse -> refund
                        ? refund(headers, config, emvResult, tek, asyncRequestId)
                        : sale(headers, config, emvResult, tek, asyncRequestId))
                .map(response -> toAuthResult(response).withAsyncRequestId(asyncRequestId));
    }

    private Single<ExchangeResponse> exchange(Map<String, String> headers, TransactionConfig config,
            EmvTransactionResult emvResult, byte[] tek, String transactionKeyEncrypted, String asyncRequestId,
            String transactionType) {
        int cvm = cvmCode(config, emvResult);
        ExchangeRequest exchangeRequest = new ExchangeRequest(
                asyncRequestId, emvResult.getPan(), cvm, transactionType);

        String encSerializedRequest;
        try {
            String serialized = gson.toJson(exchangeRequest);
            logSerialized("EXCHANGE_REQUEST", serialized);
            encSerializedRequest = AesEnvelopeCrypto.encrypt(tek, serialized);
        } catch (GeneralSecurityException e) {
            return Single.error(new HostNotReachedException("Failed to AES-encrypt the exchange request", e));
        }

        GeneralRequest request = GeneralRequest.forExchange(encSerializedRequest, asyncRequestId,
                transactionKeyEncrypted, pinKeyToSend(config), transactionType);

        return connection.exchange(headers, request)
                .flatMap(response -> decryptEnvelope(response, tek, "Exchange", ExchangeResponse.class))
                .onErrorResumeNext(e -> Single.error(exchangeError(e)));
    }

    private Single<SaleResponse> sale(Map<String, String> headers, TransactionConfig config,
            EmvTransactionResult emvResult, byte[] tek, String asyncRequestId) {
        SaleRequest saleRequest = buildSaleRequest(config, emvResult);

        String encSerializedRequest;
        try {
            String serialized = gson.toJson(saleRequest);
            logSerialized("SALE_REQUEST", serialized);
            logSerialized("CHIP_DATA (field 55 sent)", String.valueOf(config.getIccData()));
            encSerializedRequest = AesEnvelopeCrypto.encrypt(tek, serialized);
        } catch (GeneralSecurityException e) {
            return Single.error(new HostNotReachedException("Failed to AES-encrypt the sale request", e));
        }

        GeneralRequest request = GeneralRequest.forSale(
                encSerializedRequest, asyncRequestId, pinBlockToSend(config));

        // orchestration/sale (unlike exchange) 400s without mToken — see HostAppKeys#MTOKEN's
        // javadoc for what this is. The value saved on the onboarding screen wins over the fixed
        // build value (HostSettings), so an expired token can be replaced on the device.
        Map<String, String> saleHeaders = new HashMap<>(headers);
        saleHeaders.put("mToken", hostSettings.getMToken());

        return connection.sale(saleHeaders, request)
                .onErrorResumeNext(e -> Single.error(classify("Sale", e)))
                .flatMap(response -> decryptEnvelope(response, tek, "Sale", SaleResponse.class));
    }

    /** {@code orchestration/refund} — same envelope and headers as {@link #sale}. */
    private Single<SaleResponse> refund(Map<String, String> headers, TransactionConfig config,
            EmvTransactionResult emvResult, byte[] tek, String asyncRequestId) {
        RefundRequest refundRequest = buildRefundRequest(config, emvResult);

        String encSerializedRequest;
        try {
            String serialized = gson.toJson(refundRequest);
            logSerialized("REFUND_REQUEST", serialized);
            logSerialized("CHIP_DATA (field 55 sent)", String.valueOf(config.getIccData()));
            encSerializedRequest = AesEnvelopeCrypto.encrypt(tek, serialized);
        } catch (GeneralSecurityException e) {
            return Single.error(new HostNotReachedException("Failed to AES-encrypt the refund request", e));
        }

        GeneralRequest request = GeneralRequest.forSale(
                encSerializedRequest, asyncRequestId, pinBlockToSend(config));

        Map<String, String> refundHeaders = new HashMap<>(headers);
        refundHeaders.put("mToken", hostSettings.getMToken());

        // The old RefundResponse has SaleResponse's fields (responseCode, responseMessage,
        // authCode, chipData, referenceNumber, ...), so it's decoded the same way.
        return connection.refund(refundHeaders, request)
                .onErrorResumeNext(e -> Single.error(classify("Refund", e)))
                .flatMap(response -> decryptEnvelope(response, tek, "Refund", SaleResponse.class));
    }

    /**
     * A sale/refund call's error, named by its step. Certain the host couldn't have approved it —
     * {@link HostNotReachedException}, not reversed: HTTP 401/403 (the token, the auth or the
     * credentials refused) or no connection at all ({@link ConnectException},
     * {@link UnknownHostException}). Anything else may have reached the host after it was sent
     * (a timeout, a reset, 5xx, any other 4xx): {@link SaleException}, reversed.
     */
    static Throwable classify(String step, Throwable e) {
        if (e instanceof HttpException && isRefusedCaller(((HttpException) e).code())) {
            return notReached(step, e);
        }
        if (e instanceof ConnectException || e instanceof UnknownHostException) {
            return notReached(step, e);
        }
        String message = named(step, e);
        Log.w(TAG, message + " — may have reached the host");
        return new SaleException(message, e);
    }

    /** The host refused who sent the request (the token, the auth, the credentials). */
    private static boolean isRefusedCaller(int status) {
        return status == 401 || status == 403;
    }

    private static HostNotReachedException notReached(String step, Throwable e) {
        String message = named(step, e);
        Log.w(TAG, message + " — the host could not have approved it");
        return new HostNotReachedException(message, e);
    }

    /**
     * {@code "Sale: HTTP 403 Forbidden"}: the step, then the error's own message — unless that
     * already starts with the step ({@code "Exchange failed (500): …"}).
     */
    private static String named(String step, Throwable e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return message.startsWith(step) ? message : step + ": " + message;
    }

    /** Any exchange failure: the sale/refund is never sent, so nothing for the host to approve. */
    static Throwable exchangeError(Throwable e) {
        return e instanceof HostNotReachedException ? e : notReached("Exchange", e);
    }

    private <T> Single<T> decryptEnvelope(GeneralResponse response, byte[] tek, String step, Class<T> type) {
        if (response.getStatusCode() != SUCCESS_STATUS_CODE || response.getData() == null
                || response.getData().getEncSerializedResponse() == null) {
            String message = response.getMessage() != null ? response.getMessage() : step + " failed";
            String error = step + " failed (" + response.getStatusCode() + "): " + message;
            Log.w(TAG, error);
            return Single.error(isRefusedCaller(response.getStatusCode())
                    ? new HostNotReachedException(error) : new SaleException(error));
        }
        try {
            String decrypted = AesEnvelopeCrypto.decrypt(tek, response.getData().getEncSerializedResponse());
            logSerialized(step.toUpperCase(Locale.US) + "_RESPONSE", decrypted);
            return Single.just(gson.fromJson(decrypted, type));
        } catch (GeneralSecurityException e) {
            return Single.error(new SaleException("Failed to decrypt the " + step + " response", e));
        }
    }

    /**
     * Logs a request's JSON before it's AES-encrypted into {@code encSerializedRequest}, or a
     * response's after decryption — OkHttp's log only shows the encrypted envelope. Same as the
     * old app's {@code "SALE_REQUEST:Serialized: "} log. Filter Logcat by {@value #TAG}.
     *
     * <p>Debug builds only: the plain JSON holds the PAN, chip data and track 2 trailer.
     * Split into chunks because Logcat truncates a line at about 4000 characters.
     */
    private static void logSerialized(String label, String json) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        String header = label + ":Serialized: ";
        if (json.length() <= LOG_CHUNK) {
            Log.i(TAG, header + json);
            return;
        }
        int parts = (json.length() + LOG_CHUNK - 1) / LOG_CHUNK;
        for (int i = 0; i < parts; i++) {
            int from = i * LOG_CHUNK;
            Log.i(TAG, header + "(" + (i + 1) + "/" + parts + ") "
                    + json.substring(from, Math.min(json.length(), from + LOG_CHUNK)));
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

    private String extraDataJson(TransactionConfig config, EmvTransactionResult emvResult) {
        SaleExtraData extraData = new SaleExtraData(
                emvResult.getAid() != null ? emvResult.getAid() : "",
                emvResult.getEmvAppName() != null ? emvResult.getEmvAppName() : "",
                emvResult.getIssuerName(),
                pinEnterMode(config, emvResult),
                emvResult.getCardHolderName(),
                cardTypeLabel(emvResult.getEntryMethod()));
        return gson.toJson(extraData);
    }

    SaleRequest buildSaleRequest(TransactionConfig config, EmvTransactionResult emvResult) {
        CardFields card = cardFields(emvResult);

        // SaleRequest#getAmount() is a major-currency-unit double (e.g. 10.50), not
        // TransactionConfig#getAmountMinor()'s minor-unit long (cents) — see SaleRequest's
        // javadoc for how that was confirmed.
        double amountMajor = config.getAmountMinor() / 100.0;

        return new SaleRequest(
                amountMajor,
                NET_AMOUNT,
                cvmCode(config, emvResult),
                emvResult.getPan(),
                pinBlockToSend(config),
                config.getIccData(),
                card.expirationMonth,
                card.expirationYear,
                card.trailer,
                PosEntryModeCodes.forEntryMethod(emvResult.getEntryMethod()),
                cardType(emvResult),
                dcc(emvResult),
                extraDataJson(config, emvResult));
    }

    /** Same card fields as {@link #buildSaleRequest}, plus the original sale's reference number. */
    RefundRequest buildRefundRequest(TransactionConfig config, EmvTransactionResult emvResult) {
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
                PosEntryModeCodes.forEntryMethod(emvResult.getEntryMethod()),
                cardType(emvResult),
                card.trailer,
                dcc(emvResult),
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
     * The online PIN block for {@code encPinBlock} (sale envelope) and {@code pinBlock} (sale
     * body) — only when online PIN was actually entered for this sale. Offline PIN (verified by
     * the card), no CVM and signature have no PIN block, so the field is left out of the JSON
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

    /**
     * {@code cardType}: the issuer name in capitals. The old app's approved Mastercard chip sale
     * sent {@code "MASTERCARD"} with {@code extraData.issuerName} {@code "MasterCard"}.
     */
    @Nullable
    static String cardType(EmvTransactionResult emvResult) {
        String issuerName = emvResult.getIssuerName();
        return issuerName != null ? issuerName.toUpperCase(Locale.US) : null;
    }

    /**
     * {@code dcc}: {@code "true"} for Mastercard, as the old app sent for its approved Mastercard
     * chip sale. The old app's list for the other schemes isn't known, so they stay
     * {@code "false"}.
     */
    static String dcc(EmvTransactionResult emvResult) {
        String aid = emvResult.getAid();
        return aid != null && aid.toUpperCase(Locale.US).startsWith(MASTERCARD_RID) ? "true" : "false";
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

    private static AuthResult toAuthResult(SaleResponse response) {
        if (response.getResponseCode() == null) {
            // Fail closed: an answer without a response code is no approval.
            return AuthResult.failed("Online failed: the host's answer has no response code");
        }
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
