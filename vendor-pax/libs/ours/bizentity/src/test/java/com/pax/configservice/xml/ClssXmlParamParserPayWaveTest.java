package com.pax.configservice.xml;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.pax.bizentity.entity.clss.paywave.PayWaveInterFloorLimitBean;
import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * The host's real PAYWAVEPARAM section (test resource clss_param_paywave.xml): every Visa AID gets
 * the shared TERMINALCAPABILITIES (9F35, 9F33), and the contactless floor limit is
 * ContactlessFloorLimit.
 */
public class ClssXmlParamParserPayWaveTest {

    private List<PaywaveAidBean> aids;

    @Before
    public void setUp() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("clss_param_paywave.xml")) {
            assertNotNull("test resource missing", in);
            ClssXmlParamParser.Result result = ClssXmlParamParser.parse(in);
            assertNotNull(result.payWave);
            aids = result.payWave.getAid();
        }
    }

    @Test
    public void readsEveryVisaAid() {
        assertEquals(6, aids.size());
        assertEquals("A0000000031010", aids.get(0).getAid());
        assertEquals("VSDC", aids.get(0).getAppName());
        assertEquals("A0000000038010", aids.get(5).getAid());
        assertEquals("36E04000", aids.get(0).getTtq());
    }

    @Test
    public void everyAidGetsTheSharedTerminalCapabilities() {
        for (PaywaveAidBean aid : aids) {
            assertEquals(aid.getAid(), "22", aid.getTerminalType());
            assertEquals(aid.getAid(), "48", aid.getSecurityCapability());
            assertEquals(aid.getAid(), "E06848", aid.getTerminalCapability());
            assertArrayEquals(new byte[] {(byte) 0xE0, 0x68, 0x48}, aid.getTerminalCapabilityBytes());
        }
    }

    @Test
    public void limitsPerTransactionType() {
        List<PayWaveInterFloorLimitBean> limits = aids.get(0).getInterWareFloorLimit();
        assertEquals(4, limits.size());
        PayWaveInterFloorLimitBean sale = limits.get(0);
        assertEquals(0x00, sale.getTransType());
        assertEquals(60000, sale.getCvmLimit());
        assertEquals(1000000000L, sale.getTransLimit());
        assertEquals(1, sale.getCvmLimitFlag());
        PayWaveInterFloorLimitBean refund = limits.get(3);
        assertEquals(0x20, refund.getTransType());
        assertEquals(0, refund.getCvmLimitFlag());
    }

    @Test
    public void floorLimitIsTheContactlessOne() throws Exception {
        PayWaveInterFloorLimitBean limit = onlyLimit(""
                + "<TransactionType>00</TransactionType>"
                + "<TerminalFloorLimit>0</TerminalFloorLimit>"
                + "<TerminalFloorLimitSupported>0</TerminalFloorLimitSupported>"
                + "<ContactlessFloorLimit>5000</ContactlessFloorLimit>"
                + "<ContactlessFloorLimitSupported>1</ContactlessFloorLimitSupported>");

        assertEquals(5000, limit.getFloorLimit());
        assertEquals(1, limit.getFloorLimitFlag());
    }

    @Test
    public void withoutAContactlessFloorLimitTheTerminalOneIsUsed() throws Exception {
        PayWaveInterFloorLimitBean limit = onlyLimit(""
                + "<TransactionType>00</TransactionType>"
                + "<TerminalFloorLimit>7000</TerminalFloorLimit>"
                + "<TerminalFloorLimitSupported>1</TerminalFloorLimitSupported>");

        assertEquals(7000, limit.getFloorLimit());
        assertEquals(1, limit.getFloorLimitFlag());
    }

    @Test
    public void withoutTerminalCapabilitiesNothingIsMadeUp() throws Exception {
        PaywaveAidBean aid = parseOneAid("");

        assertNull(aid.getTerminalType());
        assertNull(aid.getTerminalCapability());
        assertNull(aid.getTerminalCapabilityBytes());
    }

    private static PayWaveInterFloorLimitBean onlyLimit(String entry) throws Exception {
        PaywaveAidBean aid = parseOneAid("<INTERWARELIST><Inter_WareFloorlimitByTransactionType>"
                + entry + "</Inter_WareFloorlimitByTransactionType></INTERWARELIST>");
        return aid.getInterWareFloorLimit().get(0);
    }

    private static PaywaveAidBean parseOneAid(String aidChildren) throws Exception {
        String xml = "<CLSSPARAM><PAYWAVEPARAM><AIDLIST><AID>"
                + "<ApplicationID>A0000000031010</ApplicationID>" + aidChildren
                + "</AID></AIDLIST></PAYWAVEPARAM></CLSSPARAM>";
        ClssXmlParamParser.Result result = ClssXmlParamParser.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return result.payWave.getAid().get(0);
    }
}
