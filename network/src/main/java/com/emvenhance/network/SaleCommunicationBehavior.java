package com.emvenhance.network;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;

import com.emvenhance.core.emv.EmvTransactionResult;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.transaction.TransactionType;
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

public final class SaleCommunicationBehavior implements CommunicationBehavior {

    private static final int SUCCESS_STATUS_CODE = 200;
    private static final int TEK_LENGTH_BYTES = 16;
    private static final String TRANSACTION_TYPE_SALE = "PURCHASE";
    private static final String TRANSACTION_TYPE_REFUND = "REFUND";

    private static final String TAG = "SaleComm";
    private static final int LOG_CHUNK = 3500;

    private static final int CVM_NONE = 0;
    private static final int CVM_ONLINE_PIN = 2;
    private static final int CVM_OFFLINE_PIN = 3;

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
            return type.getLabel() + " isn't supported online yet";
        }
        return null;
    }

    @Override
    public Completable acknowledge(TransactionConfig sent) {
        return closeOnline(sent, "Acknowledgement", (headers, id, type) -> connection
                .acknowledgement(headers, new AcknowledgementRequest(id, type))
                .flatMapCompletable(response ->
                        checkStatus("Acknowledgement", response.getStatusCode(), response.getMessage())));
    }

    @Override
    public Completable reverse(TransactionConfig sent) {
        return closeOnline(sent, "Reversal", (headers, id, type) -> connection
                .reverse(headers, new ReverseRequest(id, type))
                .flatMapCompletable(response ->
                        checkStatus("Reversal", response.getStatusCode(), response.getMessage())));
    }

    interface FollowUpCall {
        Completable send(Map<String, String> headers, String asyncRequestId, String transactionType);
    }

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
                .map(SaleCommunicationBehavior::toAuthResult);
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

        Map<String, String> saleHeaders = new HashMap<>(headers);
        saleHeaders.put("mToken", hostSettings.getMToken());

        return connection.sale(saleHeaders, request)
                .onErrorResumeNext(e -> Single.error(classify("Sale", e)))
                .flatMap(response -> decryptEnvelope(response, tek, "Sale", SaleResponse.class));
    }

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

        return connection.refund(refundHeaders, request)
                .onErrorResumeNext(e -> Single.error(classify("Refund", e)))
                .flatMap(response -> decryptEnvelope(response, tek, "Refund", SaleResponse.class));
    }

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

    private static boolean isRefusedCaller(int status) {
        return status == 401 || status == 403;
    }

    private static HostNotReachedException notReached(String step, Throwable e) {
        String message = named(step, e);
        Log.w(TAG, message + " — the host could not have approved it");
        return new HostNotReachedException(message, e);
    }

    private static String named(String step, Throwable e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return message.startsWith(step) ? message : step + ": " + message;
    }

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

    static int cvmCode(TransactionConfig config, EmvTransactionResult emvResult) {
        if (pinBlockToSend(config) != null) {
            return CVM_ONLINE_PIN;
        }
        if (emvResult.isHasPin()) {
            return CVM_OFFLINE_PIN;
        }
        return CVM_NONE;
    }

    @Nullable
    static String pinBlockToSend(TransactionConfig config) {
        String pinBlock = config.getOnlinePinBlock();
        return pinBlock == null || pinBlock.trim().isEmpty() ? null : pinBlock;
    }

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

    @Nullable
    static String cardType(EmvTransactionResult emvResult) {
        String issuerName = emvResult.getIssuerName();
        return issuerName != null ? issuerName.toUpperCase(Locale.US) : null;
    }

    static String dcc(EmvTransactionResult emvResult) {
        String aid = emvResult.getAid();
        return aid != null && aid.toUpperCase(Locale.US).startsWith(MASTERCARD_RID) ? "true" : "false";
    }

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
