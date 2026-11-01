package com.emvenhance.vendor.pax;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.emvenhance.core.host.AuthResult;
import com.pax.emvbase.process.entity.EOnlineResult;
import com.pax.emvbase.process.entity.OnlineResultWrapper;
import com.pax.emvbase.process.enums.TransResultEnum;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * The host's answer as the kernel takes it for the 2nd GENERATE AC ({@link PaxHostAnswer}):
 * never approved unless the host approved it.
 */
public class PaxHostAnswerTest {

    private static final byte[] ARPC = {0x11, 0x22, 0x33, 0x44, 0x00, 0x12};
    private static final byte[] SCRIPT = {0x72, 0x03, 0x01, 0x02, 0x03};

    @Test
    public void approvedCarriesTheHostsDataToTheCard() {
        OnlineResultWrapper online = PaxHostAnswer.toOnlineResult(
                AuthResult.approved("123456", "00", ARPC, SCRIPT));

        assertEquals(EOnlineResult.APPROVE.getResultCode(), online.getResultCode());
        assertEquals(TransResultEnum.RESULT_ONLINE_APPROVED, online.getTransResultEnum());
        assertEquals(EOnlineResult.APPROVE.getEmvOnlineResult(),
                online.getIssuerRspData().getOnlineResult());
        assertArrayEquals("123456".getBytes(StandardCharsets.US_ASCII),
                online.getIssuerRspData().getAuthCode());
        assertArrayEquals("00".getBytes(StandardCharsets.US_ASCII),
                online.getIssuerRspData().getRespCode());
        assertArrayEquals(ARPC, online.getIssuerRspData().getAuthData());
        assertArrayEquals(SCRIPT, online.getIssuerRspData().getScript());
    }

    @Test
    public void declinedIsADenialWithTheResponseCodeOnly() {
        OnlineResultWrapper online = PaxHostAnswer.toOnlineResult(
                AuthResult.declined("51", "Insufficient funds"));

        assertEquals(EOnlineResult.DENIAL.getResultCode(), online.getResultCode());
        assertEquals(TransResultEnum.RESULT_ONLINE_DENIED, online.getTransResultEnum());
        assertArrayEquals("51".getBytes(StandardCharsets.US_ASCII),
                online.getIssuerRspData().getRespCode());
        assertEquals("no auth code", 0, online.getIssuerRspData().getAuthCode().length);
        assertEquals("no ARPC", 0, online.getIssuerRspData().getAuthData().length);
    }

    @Test
    public void failedNotSentOrNoAnswerIsOnlineFailed() {
        for (AuthResult auth : new AuthResult[] {
                AuthResult.failed("Sale: timeout"), AuthResult.notSent("Exchange: HTTP 403"), null}) {
            OnlineResultWrapper online = PaxHostAnswer.toOnlineResult(auth);

            assertEquals(EOnlineResult.FAILED.getResultCode(), online.getResultCode());
            assertEquals(TransResultEnum.RESULT_ONLINE_FAILED, online.getTransResultEnum());
            assertEquals(EOnlineResult.FAILED.getEmvOnlineResult(),
                    online.getIssuerRspData().getOnlineResult());
        }
    }
}
