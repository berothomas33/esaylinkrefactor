package com.emvenhance.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.transaction.TransactionType;
import com.emvenhance.network.model.AcknowledgementRequest;
import com.emvenhance.network.model.AcknowledgementResponse;
import com.emvenhance.network.model.ReverseRequest;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.Test;
import retrofit2.http.POST;

/**
 * The old app's reverse and acknowledgement calls: their paths and plain-JSON bodies, a status
 * but 200 is an error, and nothing is sent for a request that never went to the host. Nulls for
 * every network dependency prove those cases touch nothing.
 */
public class ReverseAcknowledgementTest {

    private final SaleCommunicationBehavior behavior =
            new SaleCommunicationBehavior(null, null, null, null, "SN");

    @Test
    public void pathsAreTheOldApps() throws NoSuchMethodException {
        assertEquals("orchestration/reverse", path("reverse", ReverseRequest.class));
        assertEquals("orchestration/acknowledgement",
                path("acknowledgement", AcknowledgementRequest.class));
    }

    @Test
    public void bodiesAreTheOldAppsFields() {
        assertEquals(JsonParser.parseString("{\"asyncRequestId\":\"id-1\",\"transactionType\":\"PURCHASE\"}"),
                JsonParser.parseString(new Gson().toJson(new ReverseRequest("id-1", "PURCHASE"))));
        assertEquals(JsonParser.parseString("{\"asyncRequestId\":\"id-2\",\"transactionType\":\"REFUND\"}"),
                JsonParser.parseString(new Gson().toJson(new AcknowledgementRequest("id-2", "REFUND"))));
    }

    @Test
    public void acknowledgementResponseDataIsAPlainString() {
        AcknowledgementResponse response = new Gson().fromJson(
                "{\"statusCode\":200,\"message\":\"OK\",\"data\":\"done\"}", AcknowledgementResponse.class);
        assertEquals(200, response.getStatusCode());
        assertEquals("done", response.getData());
    }

    @Test
    public void onlyStatus200Succeeds() {
        SaleCommunicationBehavior.checkStatus("Reversal", 200, null).blockingAwait();
        try {
            SaleCommunicationBehavior.checkStatus("Reversal", 400, "Bad Request").blockingAwait();
            fail("400 accepted");
        } catch (SaleException e) {
            assertEquals("Reversal failed (400): Bad Request", e.getMessage());
        }
    }

    @Test
    public void nothingIsSentForARequestWithNoName() {
        TransactionConfig sale = new TransactionConfig(TransactionType.SALE, 900, EntryMethod.CHIP);
        behavior.reverse(sale).blockingAwait();
        behavior.acknowledge(sale).blockingAwait();
    }

    @Test
    public void nothingIsSentForARequestAuthorizeNeverSent() {
        TransactionConfig refundWithoutReference = new TransactionConfig(
                TransactionType.REFUND, 900, EntryMethod.CHIP).withAsyncRequestId("id-1");
        TransactionConfig cashIn = new TransactionConfig(
                TransactionType.CASH_IN, 900, EntryMethod.CHIP).withAsyncRequestId("id-2");
        behavior.reverse(refundWithoutReference).blockingAwait();
        behavior.reverse(cashIn).blockingAwait();
        behavior.acknowledge(cashIn).blockingAwait();
    }

    private static String path(String name, Class<?> body) throws NoSuchMethodException {
        Method method = HostApiConnection.class.getMethod(name, Map.class, body);
        return method.getAnnotation(POST.class).value();
    }
}
