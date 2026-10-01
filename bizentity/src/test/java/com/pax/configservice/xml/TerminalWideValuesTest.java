package com.pax.configservice.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.pax.bizentity.entity.EmvAid;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;
import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean;
import com.pax.bizentity.entity.clss.paywave.PayWaveParamBean;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Terminal-wide values from the contact ICS, applied to contactless AIDs that lack them. */
public class TerminalWideValuesTest {

    @Test
    public void prefersSameAidThenSameRidThenAny() {
        TerminalWideValues tw = new TerminalWideValues(Arrays.asList(
                contact("A0000000031010", "21", "E0F0C8", "6000F0A001"),     // Visa credit
                contact("A0000000041010", "22", "E0F8C8", "FF80F0A001"),     // Mastercard
                contact("A0000000043060", "23", "E0B8C8", "FF80F0A001")));   // Maestro

        assertEquals("22", tw.terminalType("A0000000041010"));       // same AID
        assertEquals("21", tw.terminalType("A0000000032010"));       // same RID (Visa)
        assertEquals("C8", tw.securityCapability("A0000000031010")); // 9F33 byte 3
        assertEquals("6000F0A001", tw.additionalCapability("A0000000031010"));
        assertEquals("21", tw.terminalType("A000000732"));           // no match: terminal-wide
    }

    @Test
    public void skipsContactAidsWithoutTheValue() {
        TerminalWideValues tw = new TerminalWideValues(Arrays.asList(
                contact("A0000000041010", "", "", ""),
                contact("A0000000031010", "22", "E0F8C8", "FF80F0A001")));

        assertEquals("22", tw.terminalType("A0000000041010"));
        assertEquals("C8", tw.securityCapability("A0000000041010"));
    }

    @Test
    public void nothingToGiveWithoutContactAids() {
        TerminalWideValues tw = new TerminalWideValues(null);

        assertNull(tw.terminalType("A0000000031010"));
        assertNull(tw.securityCapability("A0000000031010"));
    }

    @Test
    public void schemeConfigWinsAndGapsAreFilled() throws Exception {
        TerminalWideValues tw = new TerminalWideValues(Collections.singletonList(
                contact("A0000000041010", "21", "E0F8C8", "6000F0A001")));
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("clss_param_paypass.xml")) {
            ClssXmlParamParser.Result result = ClssXmlParamParser.parse(in, tw);

            PayPassAidBean mastercard = result.payPass.getAid().get(0);
            assertEquals("22", mastercard.getTerminalType()); // PAYPASSCONFIGURATION wins over 21
            assertEquals("FF80F0A001", mastercard.getTerminalAdditionalCapability());
            assertEquals("C8", mastercard.getSecurityCapability());
        }
    }

    @Test
    public void payWaveGetsTerminalTypeAndSecurityFromContact() {
        PaywaveAidBean visa = new PaywaveAidBean();
        visa.setAid("A0000000031010");
        PayWaveParamBean payWave = new PayWaveParamBean();
        payWave.setAid(Collections.singletonList(visa));
        ClssXmlParamParser.Result result = new ClssXmlParamParser.Result(null, payWave, null);

        ClssXmlParamParser.applyTerminalWide(result, new TerminalWideValues(Collections.singletonList(
                contact("A0000000031010", "22", "E0F8C8", "FF80F0A001"))));

        assertNotNull(visa.getTerminalType());
        assertEquals("22", visa.getTerminalType());       // was null → kernel got FF
        assertEquals("C8", visa.getSecurityCapability()); // was null
    }

    private static EmvAid contact(String aid, String terminalType, String capability, String additional) {
        EmvAid contact = new EmvAid();
        contact.setAid(aid);
        contact.setTerminalType(terminalType);
        contact.setTerminalCapability(capability);
        contact.setTerminalAdditionalCapability(additional);
        return contact;
    }
}
