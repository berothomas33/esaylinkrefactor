package com.pax.configservice.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSON;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;
import com.pax.bizentity.entity.clss.paypass.PayPassParamBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Host-XML PayPass entries get the kernel settings the XML has no tags for, from paypass.json. */
public class PayPassXmlDefaultsTest {

    private List<PayPassAidBean> bundled;

    @Before
    public void setUp() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("src/main/assets/paypass.json")),
                StandardCharsets.UTF_8);
        bundled = JSON.parseObject(json, PayPassParamBean.class).getAid();
    }

    @Test
    public void fillsTheKernelSettingsTheXmlLacks() {
        PayPassAidBean mastercard = fromXml("A0000000041010");

        List<PayPassAidBean> changed = PayPassXmlDefaults.fill(list(mastercard), bundled);

        assertEquals(1, changed.size());
        assertEquals("30", mastercard.getKernelConfig()); // On-device CVM (See Phone) on
        assertEquals("22", mastercard.getTerminalType()); // was sent to the card as FF
        assertEquals("60", mastercard.getCvmRequired());
        assertEquals("08", mastercard.getNoCvmRequired());
        assertEquals("E0", mastercard.getCardDataInput());
        assertEquals("08", mastercard.getSecurityCapability());
        assertEquals("02", mastercard.getKernelId());
        assertEquals(1, mastercard.getDataExchangeSupportFlag());
        assertFalse(mastercard.getSupportDefaultMcTermParam());
    }

    @Test
    public void keepsWhatTheXmlCarries() {
        PayPassAidBean mastercard = fromXml("A0000000041010");
        mastercard.setTacDenial("0000000001");
        mastercard.setVersion("0099");

        PayPassXmlDefaults.fill(list(mastercard), bundled);

        assertEquals("0000000001", mastercard.getTacDenial());
        assertEquals("0099", mastercard.getVersion());
    }

    @Test
    public void matchesEachAidToItsOwnScheme() {
        PayPassAidBean maestro = fromXml("A0000000043060");
        PayPassAidBean mastercardVariant = fromXml("A0000000041010FF99");

        PayPassXmlDefaults.fill(list(maestro, mastercardVariant), bundled);

        assertEquals("B0", maestro.getKernelConfig()); // Maestro: no mag-stripe mode
        assertEquals("30", mastercardVariant.getKernelConfig());
    }

    @Test
    public void leavesACompleteEntryAlone() {
        PayPassAidBean complete = bundled.get(0);
        complete.setDataExchangeSupportFlag((byte) 0);
        complete.setSupportDefaultMcTermParam(true);

        List<PayPassAidBean> changed = PayPassXmlDefaults.fill(list(complete), bundled);

        assertTrue(changed.isEmpty());
        assertEquals(0, complete.getDataExchangeSupportFlag()); // not from XML: flags untouched
        assertTrue(complete.getSupportDefaultMcTermParam());
    }

    @Test
    public void secondFillChangesNothing() {
        PayPassAidBean mastercard = fromXml("A0000000041010");
        PayPassXmlDefaults.fill(list(mastercard), bundled);

        assertTrue(PayPassXmlDefaults.fill(list(mastercard), bundled).isEmpty());
    }

    @Test
    public void noBundledDefaultsChangesNothing() {
        PayPassAidBean mastercard = fromXml("A0000000041010");

        assertTrue(PayPassXmlDefaults.fill(list(mastercard), null).isEmpty());
        assertTrue(PayPassXmlDefaults.fill(list(mastercard), Collections.emptyList()).isEmpty());
    }

    /** What ClssXmlParamParser produces: kernel settings unset, flags hardcoded. */
    private static PayPassAidBean fromXml(String aid) {
        PayPassAidBean bean = new PayPassAidBean();
        bean.setAid(aid);
        bean.setTacDenial("0400000000");
        bean.setDataExchangeSupportFlag((byte) 0);
        bean.setSupportDefaultMcTermParam(true);
        return bean;
    }

    @SafeVarargs
    private static <T> List<T> list(T... items) {
        return new java.util.ArrayList<>(java.util.Arrays.asList(items));
    }
}
