package com.pax.configservice.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.pax.bizentity.entity.CapkParamBean;
import com.pax.bizentity.entity.EmvAid;
import com.pax.bizentity.entity.EmvCapk;
import com.pax.bizentity.entity.clss.amex.AmexParamBean;
import com.pax.bizentity.entity.clss.dpas.DpasParamBean;
import com.pax.bizentity.entity.clss.eft.EFTParamBean;
import com.pax.bizentity.entity.clss.jcb.JcbParamBean;
import com.pax.bizentity.entity.clss.mir.MirParamBean;
import com.pax.bizentity.entity.clss.paypass.PayPassParamBean;
import com.pax.bizentity.entity.clss.paywave.PayWaveParamBean;
import com.pax.bizentity.entity.clss.pboc.PBOCParamBean;
import com.pax.bizentity.entity.clss.pure.PureParamBean;
import com.pax.bizentity.entity.clss.rupay.RupayParamBean;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.common.Capk;
import com.pax.emvbase.param.common.Config;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Test;

/**
 * What the kernels get from the database rows: every parameter is in the cache, even from empty
 * tables. A missing one is null in {@link EmvProcessParam} and fails every sale at terminal
 * initialization (ContactProcess.preTransProcess reads {@code getCapkParam().getCapkList()}).
 */
public class EmvParamServiceCacheTest {

    @Test
    public void everyParameterIsInTheCacheEvenFromEmptyTables() {
        EmvProcessParam param = build(new CapkParamBean());

        assertNotNull("contact AIDs", param.getEmvAidList());
        assertNotNull("CAPK", param.getCapkParam());
        assertNotNull("CAPK list", param.getCapkParam().getCapkList());
        assertNotNull("revocation list", param.getCapkParam().getCapkRevokeList());
        assertNotNull("terminal config", param.getTermConfig());
        assertNotNull("Amex", param.getAmexParam());
        assertNotNull("PayPass", param.getPayPassParam());
        assertNotNull("PayWave", param.getPayWaveParam());
        assertNotNull("DPAS", param.getDpasParam());
        assertNotNull("EFT", param.getEftParam());
        assertNotNull("JCB", param.getJcbParam());
        assertNotNull("Mir", param.getMirParam());
        assertNotNull("PBOC", param.getPbocParam());
        assertNotNull("Pure", param.getPureParam());
        assertNotNull("RuPay", param.getRuPayParam());
    }

    @Test
    public void aCapkRowReachesTheCache() {
        CapkParamBean capk = new CapkParamBean();
        capk.setCapkList(Collections.singletonList(new EmvCapk(null, "A000000003", 0x92, 1, 1,
                "AABBCC", "03", "301231", "0102030405060708090A0B0C0D0E0F1011121314")));

        EmvProcessParam param = build(capk);

        assertEquals(1, param.getCapkParam().getCapkList().size());
        Capk loaded = param.getCapkParam().getCapkList().get(0);
        assertArrayEquals(new byte[] {(byte) 0xA0, 0x00, 0x00, 0x00, 0x03}, loaded.getRid());
        assertEquals((byte) 0x92, loaded.getKeyId());
        assertTrue(param.getCapkParam().getCapkRevokeList().isEmpty());
    }

    /** The rows as loadFromDatabase reads them: every table empty but the CAPKs given. */
    private static EmvProcessParam build(CapkParamBean capk) {
        AmexParamBean amex = new AmexParamBean();
        amex.setAid(new ArrayList<>());
        amex.setProgramID(new ArrayList<>());
        PayPassParamBean payPass = new PayPassParamBean();
        payPass.setAid(new ArrayList<>());
        PayWaveParamBean payWave = new PayWaveParamBean();
        payWave.setAid(new ArrayList<>());
        DpasParamBean dpas = new DpasParamBean();
        dpas.setAid(new ArrayList<>());
        EFTParamBean eft = new EFTParamBean();
        eft.setAid(new ArrayList<>());
        JcbParamBean jcb = new JcbParamBean();
        jcb.setAid(new ArrayList<>());
        MirParamBean mir = new MirParamBean();
        mir.setAid(new ArrayList<>());
        PBOCParamBean pboc = new PBOCParamBean();
        pboc.setAid(new ArrayList<>());
        PureParamBean pure = new PureParamBean();
        pure.setAid(new ArrayList<>());
        RupayParamBean rupay = new RupayParamBean();
        rupay.setAid(new ArrayList<>());
        return new EmvParamService().toEmvProcessParam(new ArrayList<EmvAid>(), capk, new Config(),
                amex, payPass, payWave, dpas, eft, jcb, mir, pboc, pure, rupay);
    }
}
