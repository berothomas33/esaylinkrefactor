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
import com.emvenhance.network.crypto.AesEnvelopeCrypto;
import com.emvenhance.network.crypto.RsaPublicKeyEncryptor;
import com.emvenhance.network.model.AcknowledgementRequest;
import com.emvenhance.network.model.AcknowledgementResponse;
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
        this(HostApiClient.createForPayments(),
                new OnboardingClient(HostApiClient.createOnboardingForPayments()),
                new OnboardingState(context), new HostSettings(context), sn);
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
    public Single<AuthResult> connectToHost(TransactionConfig config) {
        return Single.fromCallable(() -> {
            try {
                return send(config);
            } catch (RuntimeException e) {
                return answerOf(e);
            }
        });
    }

    static AuthResult answerOf(Throwable e) {
        String message = e.getMessage();
        if (e instanceof HostNotReachedException) {
            return AuthResult.notSent(message != null ? message
                    : "Online failed: not sent to the host");
        }
        return AuthResult.failed(message != null ? message : "Online failed");
    }

    private AuthResult send(TransactionConfig config) {
        String notSent = whyNotSent(config);
        if (notSent != null) {
            Log.w(TAG, notSent + " — not sent to the host");
            return AuthResult.notSent(notSent);
        }
        EmvTransactionResult emvResult = config.getEmvResult();
        if (emvResult == null) {
            throw new HostNotReachedException(
                    "No EmvTransactionResult on TransactionConfig — the vendor EmvBehavior must "
                            + "attach one (see TransactionConfig#withEmvResult) before going online");
        }
        String hostPublicKey = onboardingState.getPublicKey();
        if (hostPublicKey == null) {
            throw new HostNotReachedException(
                    "No host public key on file — onboarding must complete before a TEK can be wrapped for the host");
        }
        if (onboardingState.getAccessToken() == null) {
            throw new HostNotReachedException(
                    "No access token on file — onboarding must complete before a sale can go online");
        }

        String accessToken;
        try {
            accessToken = onboarding.ensureValidAccessToken(HostHeaders.build(sn), onboardingState)
                    .blockingGet();
        } catch (RuntimeException e) {
            return answerOf(notReached("Token refresh", unwrapped(e)));
        }
        Map<String, String> headers = HostHeaders.buildAuthenticated(sn, accessToken);
        byte[] tek = newTek();
        String tekEncrypted = encryptTek(hostPublicKey, tek);
        String asyncRequestId = config.getAsyncRequestId() != null
                ? config.getAsyncRequestId() : UUID.randomUUID().toString();

        GeneralRequest exchangeRequest =
                prepareExchangeRequest(config, emvResult, tek, tekEncrypted, asyncRequestId);
        try {
            GeneralResponse exchangeResponse = connection.exchange(headers, exchangeRequest).blockingGet();
            decrypted(exchangeResponse, tek, "Exchange", ExchangeResponse.class);
        } catch (RuntimeException e) {
            return answerOf(exchangeError(unwrapped(e)));
        }
        return callSale(headers, config, emvResult, tek, asyncRequestId);
    }

    private static byte[] newTek() {
        byte[] tek = new byte[TEK_LENGTH_BYTES];
        new SecureRandom().nextBytes(tek);
        return tek;
    }

    private static String encryptTek(String hostPublicKey, byte[] tek) {
        try {
            return RsaPublicKeyEncryptor.encryptToHex(hostPublicKey, tek);
        } catch (GeneralSecurityException e) {
            throw new HostNotReachedException("Failed to RSA-encrypt the transaction key (TEK)", e);
        }
    }

    static Throwable unwrapped(RuntimeException e) {
        return e.getClass() == RuntimeException.class && e.getCause() != null ? e.getCause() : e;
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
        return Completable.fromAction(() -> {
            Map<String, String> headers = closingHeaders(sent, "Acknowledgement");
            if (headers == null) {
                return;
            }
            AcknowledgementResponse response = await(connection.acknowledgement(headers,
                    new AcknowledgementRequest(sent.getAsyncRequestId(), hostTransactionType(sent))));
            checkStatus("Acknowledgement", response.getStatusCode(), response.getMessage());
        });
    }

    @Override
    public Completable reverse(TransactionConfig sent) {
        return Completable.fromAction(() -> {
            Map<String, String> headers = closingHeaders(sent, "Reversal");
            if (headers == null) {
                return;
            }
            GeneralResponse response = await(connection.reverse(headers,
                    new ReverseRequest(sent.getAsyncRequestId(), hostTransactionType(sent))));
            checkStatus("Reversal", response.getStatusCode(), response.getMessage());
        });
    }

    @Nullable
    private Map<String, String> closingHeaders(TransactionConfig sent, String what) {
        if (sent.getAsyncRequestId() == null || whyNotSent(sent) != null) {
            Log.i(TAG, what + " not sent: the request never went to the host");
            return null;
        }
        String accessToken = await(
                onboarding.ensureValidAccessToken(HostHeaders.build(sn), onboardingState));
        return withMToken(HostHeaders.buildAuthenticated(sn, accessToken));
    }

    private static <T> T await(Single<T> call) {
        try {
            return call.blockingGet();
        } catch (RuntimeException e) {
            Throwable cause = unwrapped(e);
            throw cause instanceof RuntimeException
                    ? (RuntimeException) cause : new SaleException(cause.getMessage(), cause);
        }
    }

    private static boolean isRefund(TransactionConfig config) {
        return config.getType() == TransactionType.REFUND;
    }

    private static String hostTransactionType(TransactionConfig config) {
        return isRefund(config) ? TRANSACTION_TYPE_REFUND : TRANSACTION_TYPE_SALE;
    }

    private Map<String, String> withMToken(Map<String, String> headers) {
        Map<String, String> withToken = new HashMap<>(headers);
        withToken.put("mToken", hostSettings.getMToken());
        return withToken;
    }

    static void checkStatus(String what, int statusCode, @Nullable String message) {
        if (statusCode != SUCCESS_STATUS_CODE) {
            throw new SaleException(what + " failed (" + statusCode + "): "
                    + (message != null ? message : "no message"));
        }
    }

    private GeneralRequest prepareExchangeRequest(TransactionConfig config,
            EmvTransactionResult emvResult, byte[] tek, String tekEncrypted, String asyncRequestId) {
        String transactionType = hostTransactionType(config);
        ExchangeRequest exchangeRequest = new ExchangeRequest(
                asyncRequestId, emvResult.getPan(), cvmCode(config, emvResult), transactionType);
        String encSerializedRequest = encrypted(tek, "EXCHANGE_REQUEST", exchangeRequest, "exchange");
        return GeneralRequest.forExchange(encSerializedRequest, asyncRequestId,
                tekEncrypted, pinKeyToSend(config), transactionType);
    }

    private AuthResult callSale(Map<String, String> headers, TransactionConfig config,
            EmvTransactionResult emvResult, byte[] tek, String asyncRequestId) {
        boolean refund = isRefund(config);
        String step = refund ? "Refund" : "Sale";
        GeneralRequest saleRequest = prepareSaleRequest(config, emvResult, tek, asyncRequestId, step);
        Map<String, String> withToken = withMToken(headers);
        SaleResponse saleResponse;
        try {
            GeneralResponse generalResponse = refund
                    ? connection.refund(withToken, saleRequest).blockingGet()
                    : connection.sale(withToken, saleRequest).blockingGet();
            saleResponse = decrypted(generalResponse, tek, step, SaleResponse.class);
        } catch (RuntimeException e) {
            return answerOf(classify(step, unwrapped(e)));
        }
        return toAuthResult(saleResponse);
    }

    private GeneralRequest prepareSaleRequest(TransactionConfig config, EmvTransactionResult emvResult,
            byte[] tek, String asyncRequestId, String step) {
        Object body = isRefund(config)
                ? buildRefundRequest(config, emvResult) : buildSaleRequest(config, emvResult);
        String encSerializedRequest = encrypted(tek, step.toUpperCase(Locale.US) + "_REQUEST", body,
                step.toLowerCase(Locale.US));
        logSerialized("CHIP_DATA (field 55 sent)", String.valueOf(config.getIccData()));
        return GeneralRequest.forSale(encSerializedRequest, asyncRequestId, pinBlockToSend(config));
    }

    private String encrypted(byte[] tek, String logLabel, Object body, String what) {
        String serialized = gson.toJson(body);
        logSerialized(logLabel, serialized);
        try {
            return AesEnvelopeCrypto.encrypt(tek, serialized);
        } catch (GeneralSecurityException e) {
            throw new HostNotReachedException("Failed to AES-encrypt the " + what + " request", e);
        }
    }

    static RuntimeException classify(String step, Throwable e) {
        if (e instanceof HostNotReachedException || e instanceof SaleException) {
            return (RuntimeException) e;
        }
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

    static RuntimeException exchangeError(Throwable e) {
        return e instanceof HostNotReachedException
                ? (HostNotReachedException) e : notReached("Exchange", e);
    }

    private <T> T decrypted(GeneralResponse response, byte[] tek, String step, Class<T> type) {
        if (response.getStatusCode() != SUCCESS_STATUS_CODE || response.getData() == null
                || response.getData().getEncSerializedResponse() == null) {
            String message = response.getMessage() != null ? response.getMessage() : step + " failed";
            String error = step + " failed (" + response.getStatusCode() + "): " + message;
            Log.w(TAG, error);
            throw isRefusedCaller(response.getStatusCode())
                    ? new HostNotReachedException(error) : new SaleException(error);
        }
        try {
            String decrypted = AesEnvelopeCrypto.decrypt(tek, response.getData().getEncSerializedResponse());
            logSerialized(step.toUpperCase(Locale.US) + "_RESPONSE", decrypted);
            return gson.fromJson(decrypted, type);
        } catch (GeneralSecurityException e) {
            throw new SaleException("Failed to decrypt the " + step + " response", e);
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
