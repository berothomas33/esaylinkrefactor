package com.emvenhance.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.host.HostNotReachedException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import okhttp3.ResponseBody;
import org.junit.Test;
import retrofit2.HttpException;
import retrofit2.Response;

/**
 * Each online failure names its step, and is not reversed only when the host certainly couldn't
 * have approved the sale: the exchange failed, the host refused who sent it (HTTP 401/403), or
 * there was no connection at all. Anything else may have reached the host and is reversed.
 */
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

    private static void assertReversed(Throwable error) {
        assertTrue(error.getClass().getSimpleName(), error instanceof SaleException);
    }

    private static HttpException http(int code) {
        return new HttpException(Response.error(code, ResponseBody.create(null, "")));
    }
}
