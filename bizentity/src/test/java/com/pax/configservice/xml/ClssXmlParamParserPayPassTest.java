package com.pax.configservice.xml;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;
import java.io.InputStream;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * The host's real PAYPASSPARAM section (test resource clss_param_paypass.xml): every AID must get
 * the shared PAYPASSCONFIGURATION kernel settings, as stored in GreenDAO and as handed to the
 * kernel (the *Bytes getters EmvParamService reads).
 */
public class ClssXmlParamParserPayPassTest {

    private List<PayPassAidBean> aids;

    @Before
    public void setUp() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("clss_param_paypass.xml")) {
            assertNotNull("test resource missing", in);
            ClssXmlParamParser.Result result = ClssXmlParamParser.parse(in);
            assertNotNull(result.payPass);
            aids = result.payPass.getAid();
        }
    }

    @Test
    public void readsEveryAid() {
        assertEquals(6, aids.size());
        assertEquals("A0000000041010", aids.get(0).getAid());
        assertEquals("A0000000043060", aids.get(1).getAid());
        assertEquals("A000000732", aids.get(5).getAid());
    }

    @Test
    public void everyAidGetsTheSharedKernelSettings() {
        for (PayPassAidBean aid : aids) {
            String id = aid.getAid();
            assertEquals(id, "30", aid.getKernelConfig());
            assertEquals(id, "22", aid.getTerminalType());
            assertEquals(id, "60", aid.getCvmRequired());
            assertEquals(id, "08", aid.getNoCvmRequired());
            assertEquals(id, "C8", aid.getSecurityCapability());
            assertEquals(id, "E0", aid.getCardDataInput());
            assertEquals(id, "FF80F0A001", aid.getTerminalAdditionalCapability());
            assertEquals(id, "02", aid.getKernelId());
            assertEquals(id, "F0", aid.getMagCvm());
            assertEquals(id, "00", aid.getMagNoCvm()); // XML spells it MageticNoCVM
            assertEquals(id, "02", aid.getMaxTornNum());
            assertEquals(id, "0006", aid.getMaxTornLifetime());
            assertEquals(id, "01", aid.getMobileSupport()); // 9F7E
            assertEquals(id, "00", aid.getAccountType()); // 5F57 — XML spells it AccoutType
        }
    }

    @Test
    public void kernelGetsTheRightBytes() {
        PayPassAidBean mastercard = aids.get(0);

        assertEquals((byte) 0x22, mastercard.getTerminalTypeByte()); // was FF
        assertArrayEquals(new byte[] {0x30}, mastercard.getKernelConfigBytes());
        assertArrayEquals(new byte[] {0x60}, mastercard.getCvmRequiredBytes()); // online PIN + sig
        assertArrayEquals(new byte[] {0x08}, mastercard.getNoCvmRequiredBytes());
        assertArrayEquals(new byte[] {(byte) 0xC8}, mastercard.getSecurityCapabilityBytes());
        assertArrayEquals(new byte[] {0x00, 0x06}, mastercard.getMaxTornLifetimeBytes());
    }

    @Test
    public void keepsThePerAidValues() {
        PayPassAidBean mastercard = aids.get(0);

        assertEquals("MCHIP", mastercard.getAppName());
        assertEquals("0002", mastercard.getVersion());
        assertEquals("0002", mastercard.getMagVersion());
        assertEquals("0000000000", mastercard.getTacDenial());
        assertEquals("F45084800C", mastercard.getTacOnline());
        assertEquals("F45084800C", mastercard.getTacDefault());
        assertEquals("6C00800000000000", mastercard.getRiskManageData());
        assertEquals(60000, mastercard.getCvmLimit()); // 600.00: above it the kernel asks for a CVM
        assertEquals(999999999, mastercard.getTransLimit());
        assertEquals(999999999, mastercard.getCvmTransLimit());
        assertEquals(0, mastercard.getFloorLimit());
    }

    @Test
    public void tagsWithoutASourceStayUnset() {
        PayPassAidBean mastercard = aids.get(0);

        assertNull(mastercard.getDefaultUDOL());
        assertNull(mastercard.getTlvParam());
        assertNull(mastercard.getAcquirerId());
    }

    @Test
    public void padsNumericValuesToTheTagLength() {
        assertEquals("02", ClssXmlParamParser.hexByte("2", 1));
        assertEquals("0006", ClssXmlParamParser.hexByte("06", 2));
        assertEquals("012C", ClssXmlParamParser.hexByte("12C", 2));
        assertNull(ClssXmlParamParser.hexByte(null, 1));
        assertNull(ClssXmlParamParser.hexByte(" ", 1));
    }
}
