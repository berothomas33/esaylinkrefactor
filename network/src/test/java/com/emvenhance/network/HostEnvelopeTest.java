package com.emvenhance.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.emvenhance.network.crypto.AesEnvelopeCrypto;
import com.emvenhance.network.model.ExchangeRequest;
import com.emvenhance.network.model.ExchangeResponse;
import com.emvenhance.network.model.GeneralRequest;
import com.emvenhance.network.model.GeneralResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import okhttp3.ResponseBody;
import org.junit.Test;

/**
 * The exchange round trip against a fake host that knows the session's TEK: the host reads the
 * body and envelope fields it expects, and its answer, encrypted under the same TEK, comes back
 * decoded. (The RSA wrapping of the TEK in {@code open()} uses android.util.Base64, which a JVM
 * test can't run; it's unchanged by the split.)
 */
public class HostEnvelopeTest {

    private static final byte[] TEK = "0123456789ABCDEF".getBytes();

    private final Gson gson = new Gson();

    @Test
    public void exchangeIsReadableByTheHostAndItsAnswerIsDecrypted() {
        FakeHost host = new FakeHost();
        HostEnvelope envelope = new HostEnvelope(host, null);
        HostEnvelope.Session session = new HostEnvelope.Session(
                Collections.singletonMap("serialNumber", "SN1"), TEK, "WRAPPED-TEK", "request-1");

        ExchangeResponse answer = envelope.exchange(session,
                new ExchangeRequest("request-1", "5413330089020011", 1, "PURCHASE"),
                "PURCHASE", null).blockingGet();

        assertTrue(answer.isOnUs());
        assertEquals(3, answer.getBankIndex());
        assertEquals("request-1", host.envelope.get("asyncRequestId").getAsString());
        assertEquals("PURCHASE", host.envelope.get("transactionType").getAsString());
        assertEquals("WRAPPED-TEK", host.envelope.get("transactionKey").getAsString());
        assertTrue(host.body.contains("\"pan\":\"5413330089020011\""));
        assertEquals("SN1", host.headers.get("serialNumber"));
    }

    @Test
    public void nonSuccessAnswerIsAnError() {
        FakeHost host = new FakeHost();
        host.statusCode = 400;
        HostEnvelope envelope = new HostEnvelope(host, null);
        HostEnvelope.Session session = new HostEnvelope.Session(
                Collections.<String, String>emptyMap(), TEK, "WRAPPED-TEK", "request-2");

        envelope.exchange(session,
                        new ExchangeRequest("request-2", "4111111111111111", 0, "REFUND"),
                        "REFUND", null)
                .test()
                .assertError(e -> e instanceof HostException
                        && "Exchange failed (400): Bad Request".equals(e.getMessage()));
    }

    /** Decrypts the body with the known TEK and answers under it. */
    private final class FakeHost implements HostApiConnection {
        int statusCode = 200;
        JsonObject envelope;
        String body;
        Map<String, String> headers;

        @Override
        public Single<GeneralResponse> exchange(Map<String, String> headers, GeneralRequest request) {
            try {
                this.headers = new HashMap<>(headers);
                envelope = gson.toJsonTree(request).getAsJsonObject();
                body = AesEnvelopeCrypto.decrypt(TEK, envelope.get("encSerializedRequest").getAsString());
                String answer = AesEnvelopeCrypto.encrypt(TEK, "{\"onUs\":true,\"bankIndex\":3}");
                return Single.just(gson.fromJson("{\"statusCode\":" + statusCode
                        + ",\"message\":\"Bad Request\",\"data\":{\"encSerializedResponse\":\""
                        + answer + "\"}}", GeneralResponse.class));
            } catch (Exception e) {
                return Single.error(e);
            }
        }

        @Override
        public Single<GeneralResponse> sale(Map<String, String> headers, GeneralRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Single<GeneralResponse> refund(Map<String, String> headers, GeneralRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Single<ResponseBody> emvFileDownload(Map<String, String> headers, String posType) {
            throw new UnsupportedOperationException();
        }
    }
}
