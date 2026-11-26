package com.emvenhance.network.onboarding;

import android.util.Log;

import com.emvenhance.core.host.TerminalKeys;
import com.emvenhance.core.host.TerminalKeys.Key;
import com.emvenhance.network.HostApiClient;
import com.emvenhance.network.HostAppKeys;
import com.emvenhance.network.HostHeaders;
import com.emvenhance.network.crypto.PedEnvelopeCrypto;
import com.emvenhance.network.crypto.RsaPublicKeyEncryptor;
import com.emvenhance.network.model.GeneralResponse;
import com.emvenhance.network.onboarding.model.ConfirmOnboardingOnlineRequest;
import com.emvenhance.network.onboarding.model.CreateTokenRequest;
import com.emvenhance.network.onboarding.model.CreateTokenResponse;
import com.emvenhance.network.onboarding.model.CreateTokenResult;
import com.emvenhance.network.onboarding.model.EncryptedOnboardingRequest;
import com.emvenhance.network.onboarding.model.HandshakeOnboardingOnlineRequest;
import com.emvenhance.network.onboarding.model.KeyExchangeRequest;
import com.emvenhance.network.onboarding.model.KeyExchangeResponse;
import com.emvenhance.network.onboarding.model.OnboardingIdentity;
import com.emvenhance.network.onboarding.model.OnboardingRequest;
import com.emvenhance.network.onboarding.model.OnboardingResponse;
import com.emvenhance.network.onboarding.model.OnboardingResult;
import com.emvenhance.network.onboarding.model.OnboardingStatusResponse;
import com.google.gson.Gson;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Single;

public final class OnboardingClient {

    public static final int SUCCESS_STATUS_CODE = 200;

    private static final String TAG = "Onboarding";
    /** The pilot's KEK: AES-192, as card acceptance's Encryptor.generateAESKey (KeyGenerator 192). */
    private static final int KEK_LENGTH_BYTES = 24;
    private static final int KCV_COMPARED_HEX = 6;

    private final OnboardingApiConnection connection;

    public OnboardingClient() {
        this(HostApiClient.createOnboarding());
    }

    public OnboardingClient(OnboardingApiConnection connection) {
        this.connection = connection;
    }

    public Single<CreateTokenResponse> createToken(Map<String, String> headers, String serviceAccount) {
        return connection.createToken(headers, new CreateTokenRequest(serviceAccount));
    }

    /**
     * The RKI onboarding, step for step as card acceptance {@code develop_rki_pilot}
     * ({@code ConfigurationActivity.offlineHandShake → … → onboardingConfirmation →
     * processAuthentication}). ①–③ stand in for real RKI: they put the KBPK in the PED; once
     * the KBPK is injected by RKI, the cycle starts at ④.
     *
     * <pre>
     * ① GET  rki/offline/handshake       → SACS
     * ② POST rki/offline/onboarding      challenge → publicKey (HMAC(publicKey + challenge, SACS))
     * ③ POST rki/offline/key-exchange    RSA(publicKey, KEK) → KBPK as TR-31 under the KEK, KCV
     * ④ GET  rki/onboarding/handshake    → APP_TDK as TR-31 under the KBPK
     * ⑤ POST rki/onboarding              APP_TDK(identity) → TDK, TPK (TR-31) + KCVs,
     *                                     APP_TDK(serviceAccount), HMAC(…, sn + systemKey + aggregatorKey)
     * ⑥ POST rki/onboarding/confirm      the same APP_TDK(identity)
     * ⑦ POST authorization/token/pos     serviceAccount → access token
     * </pre>
     *
     * Every check fails closed: a bad signature, a refused key write or a KCV mismatch ends
     * the cycle with the terminal not onboarded.
     */
    public Single<CreateTokenResult> runOfflineCycle(String sn, OnboardingState state) {
        return Single.fromCallable(() -> {
            state.resetCompletion();
            Map<String, String> headers = HostHeaders.build(sn);
            TerminalKeys keys = PedEnvelopeCrypto.keys();

            bootstrapKbpk(headers, keys, state);

            String appTdk = checked(connection.onboardingHandshake(headers).blockingGet());
            keys.writeKeyBlock(Key.APP_TDK, required(appTdk, "Handshake returned no APP_TDK"));

            Map<String, String> encryptedHeaders = HostHeaders.buildForEncryptedOnboarding();
            EncryptedOnboardingRequest identity = new EncryptedOnboardingRequest(
                    PedEnvelopeCrypto.encrypt(Key.APP_TDK, new Gson().toJson(new OnboardingIdentity(
                            HostAppKeys.SYSTEM_APP_KEY, HostAppKeys.AGGREGATOR_APP_KEY, sn))));
            OnboardingResponse onboarding = connection.onboarding(encryptedHeaders, identity).blockingGet();
            checked(onboarding.getStatusCode(), onboarding.getMessage());
            String serviceAccount = injectSessionKeys(onboarding, keys, sn);
            state.saveServiceAccount(serviceAccount);

            checked(connection.onboardingConfirm(encryptedHeaders, identity).blockingGet());
            Log.i(TAG, "RKI onboarding confirmed — TDK and TPK in the PED");

            CreateTokenResponse tokenResponse = createToken(headers, serviceAccount).blockingGet();
            checked(tokenResponse.getStatusCode(), tokenResponse.getMessage());
            CreateTokenResult token = requireTokenData(tokenResponse).blockingGet();
            state.saveAccessToken(token.getAccessToken());
            if (token.getExpiration() != null) {
                state.saveTokenExpiration(token.getExpiration());
            }
            state.markOfflineOnboarded();
            return token;
        });
    }

    /** ①–③: the SACS, the host's public key, then the KEK out and the KBPK back. */
    private void bootstrapKbpk(Map<String, String> headers, TerminalKeys keys, OnboardingState state) {
        String sacs = required(checked(connection.offlineHandshake(headers).blockingGet()),
                "Handshake returned no SACS");
        state.saveSacs(sacs);

        String challenge = ChallengeGenerator.generate();
        OnboardingResponse offline = connection.offlineOnboarding(headers, new OnboardingRequest(challenge))
                .blockingGet();
        checked(offline.getStatusCode(), offline.getMessage());
        OnboardingResult data = offline.getData();
        String publicKey = data != null ? data.getPublicKey() : null;
        if (publicKey == null || !HmacSigner.verify(publicKey + challenge, sacs, data.getSignature())) {
            throw new OnboardingException(offline.getStatusCode(), "Offline onboarding signature verification failed");
        }
        state.saveChallenge(challenge);
        state.savePublicKey(publicKey);

        byte[] kek = new byte[KEK_LENGTH_BYTES];
        new SecureRandom().nextBytes(kek);
        try {
            String encryptedKek;
            try {
                encryptedKek = RsaPublicKeyEncryptor.encryptToHex(publicKey, kek);
            } catch (GeneralSecurityException e) {
                throw new OnboardingException(0, "Failed to RSA-encrypt the KEK: " + e.getMessage());
            }
            KeyExchangeResponse exchange = connection.keyExchange(headers, new KeyExchangeRequest(encryptedKek))
                    .blockingGet();
            checked(exchange.getStatusCode(), exchange.getMessage());
            KeyExchangeResponse.Data keyData = exchange.getData();
            String kbpk = required(keyData != null ? keyData.getMak() : null, "Key exchange returned no KBPK");
            keys.writeClearKek(kek);
            keys.writeKeyBlock(Key.KBPK, kbpk);
            requireKcv(keys, Key.KBPK, keyData.getMakKcv());
        } finally {
            Arrays.fill(kek, (byte) 0);
        }
    }

    /** ⑤: the service account opened, the signature checked, then TDK and TPK in and checked. */
    private static String injectSessionKeys(OnboardingResponse onboarding, TerminalKeys keys, String sn) {
        OnboardingResult data = onboarding.getData();
        if (data == null || data.getServiceAccount() == null || data.getTransactionKey() == null
                || data.getPinKey() == null) {
            throw new OnboardingException(onboarding.getStatusCode(), "Onboarding response missing keys");
        }
        String serviceAccount = PedEnvelopeCrypto.decrypt(Key.APP_TDK, data.getServiceAccount());
        String signed = data.getTransactionKey() + data.getTransactionKeyKcv() + data.getPinKey()
                + data.getPinKeyKcv() + serviceAccount;
        String hmacKey = sn + HostAppKeys.SYSTEM_APP_KEY + HostAppKeys.AGGREGATOR_APP_KEY;
        if (!HmacSigner.verify(signed, hmacKey, data.getSignature())) {
            throw new OnboardingException(onboarding.getStatusCode(), "Onboarding signature verification failed");
        }
        keys.writeKeyBlock(Key.TDK, data.getTransactionKey());
        keys.writeKeyBlock(Key.TPK, data.getPinKey());
        requireKcv(keys, Key.TDK, data.getTransactionKeyKcv());
        requireKcv(keys, Key.TPK, data.getPinKeyKcv());
        return serviceAccount;
    }

    /** Card acceptance's isKcvMatch: the PED's first 6 hex digits against the host's KCV. */
    static boolean kcvMatches(@Nullable String expected, @Nullable String actual) {
        if (expected == null || actual == null || actual.length() < KCV_COMPARED_HEX) {
            return false;
        }
        String normalized = expected.replace(" ", "").replace(":", "").trim().toUpperCase(Locale.US);
        return actual.substring(0, KCV_COMPARED_HEX).equalsIgnoreCase(normalized);
    }

    private static void requireKcv(TerminalKeys keys, Key key, @Nullable String expected) {
        if (!kcvMatches(expected, keys.kcv(key))) {
            throw new OnboardingException(0, key + " KCV does not match the host's — key not trusted");
        }
        Log.i(TAG, key + " KCV matches");
    }

    @Nullable
    private static String checked(OnboardingStatusResponse response) {
        checked(response.getStatusCode(), response.getMessage());
        return response.getData();
    }

    private static void checked(int statusCode, @Nullable String message) {
        if (statusCode != SUCCESS_STATUS_CODE) {
            throw new OnboardingException(statusCode, message != null ? message : "Onboarding step failed");
        }
    }

    private static String required(@Nullable String value, String whenMissing) {
        if (value == null || value.isEmpty()) {
            throw new OnboardingException(0, whenMissing);
        }
        return value;
    }

    public Single<String> ensureValidAccessToken(Map<String, String> headers, OnboardingState state) {
        String accessToken = state.getAccessToken();
        if (accessToken != null && state.isTokenValid()) {
            return Single.just(accessToken);
        }
        String serviceAccount = state.getServiceAccount();
        if (serviceAccount == null) {
            return Single.error(new OnboardingException(0,
                    "No service account on file — full onboarding must complete before a token can be refreshed"));
        }
        return createToken(headers, serviceAccount)
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                .flatMap(this::requireTokenData)
                .doOnSuccess(data -> {
                    state.saveAccessToken(data.getAccessToken());
                    if (data.getExpiration() != null) {
                        state.saveTokenExpiration(data.getExpiration());
                    }
                })
                .map(CreateTokenResult::getAccessToken);
    }

    private Single<CreateTokenResult> requireTokenData(CreateTokenResponse response) {
        CreateTokenResult data = response.getData();
        if (data == null || data.getAccessToken() == null) {
            return Single.error(new OnboardingException(response.getStatusCode(),
                    "createToken response missing accessToken"));
        }
        return Single.just(data);
    }

    public Single<OnboardingStatusResponse> handshakeOnline(Map<String, String> headers,
            String onboardingType, String onboardingKey) {
        return connection.handshakeOnboardingOnline(headers,
                new HandshakeOnboardingOnlineRequest(onboardingType, onboardingKey));
    }

    public Single<GeneralResponse> onboardOnline(Map<String, String> headers) {
        return connection.onboardingOnline(headers);
    }

    public Single<OnboardingStatusResponse> confirmOnline(Map<String, String> headers,
            String sacs, String bankCodes) {
        return connection.confirmOnboardingOnline(headers,
                new ConfirmOnboardingOnlineRequest(sacs, bankCodes));
    }

    public Single<OnboardingStatusResponse> runOnlineCycle(Map<String, String> headers,
            String onboardingType, String onboardingKey, String sacs, String bankCodes) {
        return handshakeOnline(headers, onboardingType, onboardingKey)
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                .flatMap(r -> onboardOnline(headers))
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                .flatMap(r -> confirmOnline(headers, sacs, bankCodes))
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()));
    }

    private static <T> Single<T> requireSuccess(T response, int statusCode, @Nullable String message) {
        if (statusCode != SUCCESS_STATUS_CODE) {
            return Single.error(new OnboardingException(statusCode,
                    message != null ? message : "Onboarding step failed"));
        }
        return Single.just(response);
    }
}
