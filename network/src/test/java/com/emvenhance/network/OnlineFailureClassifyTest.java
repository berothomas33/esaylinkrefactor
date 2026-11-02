package com.emvenhance.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.transaction.TransactionType;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import okhttp3.ResponseBody;
import org.junit.Test;
import retrofit2.HttpException;
import retrofit2.Response;

public class OnlineFailureClassifyTest {

    @Test
    public void saleRefusedWith403IsNotReversed() {
        Throwable error = SaleCommunicationBehavior.classify("Sale", http(403));
        assertTrue(error instanceof HostNotReachedException);
        assertTrue(error.getMessage(), error.getMessage().startsWith("Sale: HTTP 403"));
    }

    @Test
    public void saleRefusedWith401IsNotReversed() {
        assertTrue(SaleCommunicationBehavior.classify("Refund", http(401))
                instanceof HostNotReachedException);
    }

    @Test
    public void noConnectionIsNotReversed() {
        assertTrue(SaleCommunicationBehavior.classify("Sale", new ConnectException("refused"))
                instanceof HostNotReachedException);
        assertTrue(SaleCommunicationBehavior.classify("Sale", new UnknownHostException("host"))
                instanceof HostNotReachedException);
    }

    @Test
    public void whatMayHaveReachedTheHostIsReversed() {
        assertReversed(SaleCommunicationBehavior.classify("Sale", http(500)));
        assertReversed(SaleCommunicationBehavior.classify("Sale", http(400)));
        assertReversed(SaleCommunicationBehavior.classify("Sale",
                new SocketTimeoutException("timeout")));
        assertReversed(SaleCommunicationBehavior.classify("Sale", new IOException("reset")));
        assertEquals("Sale: reset",
                SaleCommunicationBehavior.classify("Sale", new IOException("reset")).getMessage());
    }

    @Test
    public void anyExchangeFailureIsNotReversedAndNamesTheExchange() {
        Throwable http = SaleCommunicationBehavior.exchangeError(http(403));
        assertTrue(http instanceof HostNotReachedException);
        assertTrue(http.getMessage(), http.getMessage().startsWith("Exchange: HTTP 403"));

        Throwable envelope = SaleCommunicationBehavior.exchangeError(
                new SaleException("Exchange failed (500): boom"));
        assertTrue(envelope instanceof HostNotReachedException);
        assertEquals("Exchange failed (500): boom", envelope.getMessage());

        assertTrue(SaleCommunicationBehavior.exchangeError(new SocketTimeoutException("t"))
                instanceof HostNotReachedException);
    }

    @Test
    public void notReachedIsAnsweredNotSent() {
        AuthResult answer = SaleCommunicationBehavior.answerOf(
                SaleCommunicationBehavior.classify("Sale", http(403)));
        assertTrue(answer.isFailed());
        assertTrue("not reversed", answer.isNotSent());
        assertTrue(answer.getMessage(), answer.getMessage().startsWith("Sale: HTTP 403"));
    }

    @Test
    public void whatMayHaveReachedTheHostIsAnsweredFailedAndReversed() {
        AuthResult answer = SaleCommunicationBehavior.answerOf(
                SaleCommunicationBehavior.classify("Sale", http(500)));
        assertTrue(answer.isFailed());
        assertFalse("reversed", answer.isNotSent());
        assertFalse(answer.isApproved());
    }

    @Test
    public void anUnexpectedErrorIsAnsweredFailedAndReversed() {
        AuthResult answer = SaleCommunicationBehavior.answerOf(new IllegalStateException());
        assertTrue(answer.isFailed());
        assertFalse(answer.isNotSent());
        assertEquals("Online failed", answer.getMessage());
    }

    @Test
    public void authorizeNeverFailsANeverSentRequestIsAnsweredNotSent() {
        SaleCommunicationBehavior host = new SaleCommunicationBehavior(null, null, null, null, "SN");
        AuthResult answer = host.authorize(
                new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP)).blockingGet();
        assertTrue("no EmvTransactionResult: never sent", answer.isNotSent());
    }

    private static void assertReversed(Throwable error) {
        assertTrue(error.getClass().getSimpleName(), error instanceof SaleException);
    }

    private static HttpException http(int code) {
        return new HttpException(Response.error(code, ResponseBody.create(null, "")));
    }
}
