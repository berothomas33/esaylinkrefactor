package com.emvenhance.network;

import android.util.Log;
import androidx.annotation.Nullable;

import com.emvenhance.network.crypto.AesEnvelopeCrypto;
import com.emvenhance.network.crypto.RsaPublicKeyEncryptor;
import com.emvenhance.network.model.ExchangeRequest;
import com.emvenhance.network.model.ExchangeResponse;
import com.emvenhance.network.model.GeneralRequest;
import com.emvenhance.network.model.GeneralResponse;
import com.google.gson.Gson;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.reactivex.rxjava3.core.Single;

/**
 * The encrypted envelope every host transaction request travels in, the same for sale, refund
 * and whatever comes next. A transaction opens one {@link Session}: a fresh 16-byte transaction
 * key (TEK), RSA-wrapped with the host's public key from onboarding, and an
 * {@code asyncRequestId} its exchange and its request share. {@link #exchange} hands the host the
 * TEK (and the PIN key, for online PIN); {@link #send} carries the request itself. Each body is
 * AES-encrypted under the TEK into {@code encSerializedRequest}, and the answer decrypted with it.
 *
 * <p>The old project supplied a {@code paymentAsyncID} from whatever aggregator launched
 * {@code SaleActivity} — this codebase has no such caller, so {@link #open} generates a fresh
 * {@link UUID} per transaction.
 */
final class HostEnvelope {

    /** One orchestration endpoint, e.g. {@code connection::sale} or {@code connection::refund}. */
    interface Endpoint {
        Single<GeneralResponse> call(Map<String, String> headers, GeneralRequest request);
    }

    /** One transaction's envelope keys and id. */
    static final class Session {
        final Map<String, String> headers;
        final byte[] tek;
        final String transactionKeyEncrypted;
        final String asyncRequestId;

        Session(Map<String, String> headers, byte[] tek, String transactionKeyEncrypted,
                String asyncRequestId) {
            this.headers = headers;
            this.tek = tek;
            this.transactionKeyEncrypted = transactionKeyEncrypted;
            this.asyncRequestId = asyncRequestId;
        }
    }

    /** Logcat tag for the plain request/response JSON — see {@link #logSerialized}. */
    static final String TAG = "HostComm";

    private static final int SUCCESS_STATUS_CODE = 200;
    private static final int TEK_LENGTH_BYTES = 16;
    /** Logcat truncates a single line at ~4000 characters. */
    private static final int LOG_CHUNK = 3500;

    private final HostApiConnection connection;
    private final HostSettings hostSettings;
    private final Gson gson = new Gson();

    HostEnvelope(HostApiConnection connection, HostSettings hostSettings) {
        this.connection = connection;
        this.hostSettings = hostSettings;
    }

    /** A new session for one transaction: a fresh TEK, RSA-wrapped for the host, and a new id. */
    Session open(Map<String, String> headers, String hostPublicKey) {
        byte[] tek = new byte[TEK_LENGTH_BYTES];
        new SecureRandom().nextBytes(tek);
        try {
            String transactionKeyEncrypted = RsaPublicKeyEncryptor.encryptToHex(hostPublicKey, tek);
            return new Session(headers, tek, transactionKeyEncrypted, UUID.randomUUID().toString());
        } catch (GeneralSecurityException e) {
            throw new HostException("Failed to RSA-encrypt the transaction key (TEK)", e);
        }
    }

    /**
     * {@code orchestration/exchange}: establishes the session's TEK with the host (and the PIN
     * key, when online PIN was collected) for {@code transactionType}.
     */
    Single<ExchangeResponse> exchange(Session session, ExchangeRequest body, String transactionType,
            @Nullable String pinKey) {
        return Single.defer(() -> {
            String encrypted = encrypt(session, "Exchange", body);
            GeneralRequest request = GeneralRequest.forExchange(encrypted, session.asyncRequestId,
                    session.transactionKeyEncrypted, pinKey, transactionType);
            return connection.exchange(session.headers, request);
        }).flatMap(response -> decrypt(response, session, "Exchange", ExchangeResponse.class));
    }

    /**
     * Sends one request body through {@code endpoint} in the session's envelope, with the online
     * PIN block when there is one. The orchestration request endpoints (unlike exchange) 400
     * without {@code mToken} — see {@link HostAppKeys#MTOKEN}. The value saved on the onboarding
     * screen wins over the fixed build value ({@link HostSettings}), so an expired token can be
     * replaced on the device.
     *
     * @param step names the request in logs and errors: "Sale", "Refund", ...
     */
    <T> Single<T> send(Session session, String step, Object body, @Nullable String pinBlock,
            Endpoint endpoint, Class<T> responseType) {
        return Single.defer(() -> {
            String encrypted = encrypt(session, step, body);
            GeneralRequest request = GeneralRequest.forSale(encrypted, session.asyncRequestId, pinBlock);
            Map<String, String> headers = new HashMap<>(session.headers);
            headers.put("mToken", hostSettings.getMToken());
            return endpoint.call(headers, request);
        }).flatMap(response -> decrypt(response, session, step, responseType));
    }

    private String encrypt(Session session, String step, Object body) {
        String serialized = gson.toJson(body);
        logSerialized(step.toUpperCase(Locale.US) + "_REQUEST", serialized);
        try {
            return AesEnvelopeCrypto.encrypt(session.tek, serialized);
        } catch (GeneralSecurityException e) {
            throw new HostException("Failed to AES-encrypt the "
                    + step.toLowerCase(Locale.US) + " request", e);
        }
    }

    private <T> Single<T> decrypt(GeneralResponse response, Session session, String step, Class<T> type) {
        if (response.getStatusCode() != SUCCESS_STATUS_CODE || response.getData() == null
                || response.getData().getEncSerializedResponse() == null) {
            String message = response.getMessage() != null ? response.getMessage() : step + " failed";
            return Single.error(new HostException(step + " failed (" + response.getStatusCode() + "): " + message));
        }
        try {
            String decrypted = AesEnvelopeCrypto.decrypt(session.tek, response.getData().getEncSerializedResponse());
            logSerialized(step.toUpperCase(Locale.US) + "_RESPONSE", decrypted);
            return Single.just(gson.fromJson(decrypted, type));
        } catch (GeneralSecurityException e) {
            return Single.error(new HostException("Failed to decrypt the " + step + " response", e));
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
    static void logSerialized(String label, String json) {
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
}
