package com.pax.configservice.xml;

import com.pax.bizentity.entity.clss.amex.AmexAidBean;
import com.pax.bizentity.entity.clss.amex.AmexDrlBean;
import com.pax.bizentity.entity.clss.amex.AmexParamBean;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;
import com.pax.bizentity.entity.clss.paypass.PayPassParamBean;
import com.pax.bizentity.entity.clss.paywave.PayWaveInterFloorLimitBean;
import com.pax.bizentity.entity.clss.paywave.PayWaveParamBean;
import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean;
import com.pax.bizentity.entity.clss.paywave.PaywaveDrlBean;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import androidx.annotation.Nullable;

/**
 * Parses a PAX {@code clss_param.clss} (contactless) parameter file into the same
 * {@link PayPassParamBean}/{@link PayWaveParamBean}/{@link AmexParamBean} shapes
 * {@code ConfigInit} currently loads from the bundled {@code paypass.json}/{@code paywave.json}/
 * {@code amex.json}. {@code PAYPASSPARAM} → PayPass, {@code PAYWAVEPARAM} → PayWave,
 * {@code EXPRESSPAYPARAM} → Amex ExpressPay — per-scheme sections are each optional; a
 * deployment's export only carries the schemes it actually has configured, so a missing section
 * yields a {@code null} bean for that scheme rather than an error.
 *
 * <p><b>No reference parser was available to build this against</b> (see
 * {@link EmvXmlParamParser}'s javadoc for the same caveat) — field mappings below were derived
 * from tag names, the bundled JSON assets' shapes, and the target entities' own doc comments
 * (e.g. {@code PayPassAidBean.transLimit}'s "Reader contactless transaction limit (No On-device
 * CVM)" comment, which is word-for-word what {@code ContactlessTransactionLimit_NoOnDevice}
 * describes). Only DPAS/EFT/JCB/MIR/PBOC/PURE/RUPAY are out of scope here — this sample file
 * doesn't contain those sections, so there was no tag structure to verify a mapping against.
 *
 * <p>Several bean fields (PayPass: {@code kernelConfig}/{@code cardDataInput}/
 * {@code cvmRequired}/{@code noCvmRequired}/{@code kernelId}/{@code tlvParam}/
 * {@code defaultUDOL}/{@code deviceSN}/{@code dsOperatorId}; Amex: {@code exFunction}/
 * {@code aucRFU}) have no corresponding tag anywhere in this file and are left unset — not
 * defaulted to a guessed value.
 */
public final class ClssXmlParamParser {

    private ClssXmlParamParser() {
    }

    public static final class Result {
        @Nullable
        public final PayPassParamBean payPass;
        @Nullable
        public final PayWaveParamBean payWave;
        @Nullable
        public final AmexParamBean amex;

        Result(@Nullable PayPassParamBean payPass, @Nullable PayWaveParamBean payWave,
                @Nullable AmexParamBean amex) {
            this.payPass = payPass;
            this.payWave = payWave;
            this.amex = amex;
        }
    }

    public static Result parse(InputStream in)
            throws ParserConfigurationException, IOException, SAXException {
        Element root = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(in)
                .getDocumentElement();

        Element payPassEl = XmlDomUtils.firstChild(root, "PAYPASSPARAM");
        Element payWaveEl = XmlDomUtils.firstChild(root, "PAYWAVEPARAM");
        Element expressPayEl = XmlDomUtils.firstChild(root, "EXPRESSPAYPARAM");

        return new Result(
                payPassEl == null ? null : parsePayPass(payPassEl),
                payWaveEl == null ? null : parsePayWave(payWaveEl),
                expressPayEl == null ? null : parseAmex(expressPayEl));
    }

    // ─── PayPass (Mastercard) ─────────────────────────────────────────────

    private static PayPassParamBean parsePayPass(Element payPassEl) {
        Element aidList = XmlDomUtils.firstChild(payPassEl, "AIDLIST");
        List<PayPassAidBean> aids = new ArrayList<>();
        for (Element aidEl : XmlDomUtils.children(aidList, "AID")) {
            aids.add(parsePayPassAid(aidEl));
        }
        PayPassParamBean bean = new PayPassParamBean();
        bean.setAid(aids);
        return bean;
    }

    private static PayPassAidBean parsePayPassAid(Element aidEl) {
        return new PayPassAidBean(
                null,
                XmlDomUtils.text(aidEl, "LocalAIDName", ""),
                XmlDomUtils.text(aidEl, "ApplicationID", ""),
                XmlDomUtils.byteOf(aidEl, "PartialAIDSelection", 0),
                XmlDomUtils.longOf(aidEl, "ContactlessTransactionLimit_NoOnDevice", 0),
                flagFor(aidEl, "ContactlessTransactionLimit_NoOnDevice"),
                XmlDomUtils.longOf(aidEl, "ContactlessTransactionLimit_OnDevice", 0),
                XmlDomUtils.longOf(aidEl, "ContactlessFloorLimit", 0),
                flagFor(aidEl, "ContactlessFloorLimit"),
                XmlDomUtils.longOf(aidEl, "ContactlessCVMLimit", 0),
                flagFor(aidEl, "ContactlessCVMLimit"),
                XmlDomUtils.text(aidEl, "TACDenial"),
                XmlDomUtils.text(aidEl, "TACOnline"),
                XmlDomUtils.text(aidEl, "TACDefault"),
                null, // acquirerId — no source tag
                XmlDomUtils.text(aidEl, "TerminalAIDVersion"),
                XmlDomUtils.text(aidEl, "TerminalRisk"),
                null, // terminalType — no per-AID or shared source tag found for PayPass
                null, // terminalAdditionalCapability
                null, // kernelConfig
                null, // cardDataInput
                null, // cvmRequired
                null, // noCvmRequired
                null, // securityCapability
                XmlDomUtils.text(aidEl, "MagneticApplicationVersionNumber"),
                null, // magCvm
                null, // magNoCvm
                null, // kernelId
                null, // kernelIdBytes (@Transient)
                (byte) 0, // dataExchangeSupportFlag
                null, // tlvParam
                null, // defaultUDOL
                0L, // refundVoidFloorLimit
                null, // refundVoidTacDenial
                true, // supportDefaultMcTermParam — matches PayPassAidBean's own field default
                null, // maxTornNum
                null, // maxTornLifetime
                null, // deviceSN
                null); // dsOperatorId
    }

    // ─── PayWave (Visa) ────────────────────────────────────────────────────

    private static PayWaveParamBean parsePayWave(Element payWaveEl) {
        Element aidList = XmlDomUtils.firstChild(payWaveEl, "AIDLIST");
        List<PaywaveAidBean> aids = new ArrayList<>();
        for (Element aidEl : XmlDomUtils.children(aidList, "AID")) {
            PaywaveAidBean aid = parsePayWaveAid(aidEl);
            List<PayWaveInterFloorLimitBean> floorLimits = new ArrayList<>();
            Element interWareList = XmlDomUtils.firstChild(aidEl, "INTERWARELIST");
            for (Element entry : XmlDomUtils.children(interWareList, "Inter_WareFloorlimitByTransactionType")) {
                floorLimits.add(parsePayWaveFloorLimit(entry));
            }
            aid.setInterWareFloorLimit(floorLimits);
            aids.add(aid);
        }

        Element programIdList = XmlDomUtils.firstChild(payWaveEl, "PROGRAMIDLIST");
        List<PaywaveDrlBean> programs = new ArrayList<>();
        for (Element programEl : XmlDomUtils.children(programIdList, "PROGRAMID")) {
            programs.add(parsePayWaveProgram(programEl));
        }

        PayWaveParamBean bean = new PayWaveParamBean();
        bean.setAid(aids);
        bean.setProgramID(programs);
        return bean;
    }

    private static PaywaveAidBean parsePayWaveAid(Element aidEl) {
        return new PaywaveAidBean(
                null,
                XmlDomUtils.text(aidEl, "LocalAIDName", ""),
                XmlDomUtils.text(aidEl, "ApplicationID", ""),
                XmlDomUtils.byteOf(aidEl, "PartialAIDSelection", 0),
                XmlDomUtils.text(aidEl, "TerminalAIDVersion"),
                null, // terminalType — no source tag per-AID for PayWave
                XmlDomUtils.text(aidEl, "ReaderTTQ"),
                XmlDomUtils.byteOf(aidEl, "CryptogramVersion17Supported", 0),
                XmlDomUtils.byteOf(aidEl, "StatusCheckSupported", 0),
                XmlDomUtils.byteOf(aidEl, "ZeroAmountNoAllowed", 0),
                null, // securityCapability — no source tag
                (byte) 0, // domesticOnly
                (byte) 0); // enDDAVerNo
    }

    private static PayWaveInterFloorLimitBean parsePayWaveFloorLimit(Element entry) {
        return new PayWaveInterFloorLimitBean(
                null,
                null, // paywaveAidId — set by EmvParamService after the parent AID gets its id
                (byte) hexToInt(XmlDomUtils.text(entry, "TransactionType", "00")),
                XmlDomUtils.longOf(entry, "TerminalFloorLimit", 0),
                XmlDomUtils.longOf(entry, "ContactlessTransactionLimit", 0),
                XmlDomUtils.longOf(entry, "ContactlessCVMLimit", 0),
                XmlDomUtils.byteOf(entry, "ContactlessTransactionLimitSupported", 0),
                XmlDomUtils.byteOf(entry, "CVMLimitSupported", 0),
                XmlDomUtils.byteOf(entry, "TerminalFloorLimitSupported", 0));
    }

    private static PaywaveDrlBean parsePayWaveProgram(Element programEl) {
        return new PaywaveDrlBean(
                XmlDomUtils.text(programEl, "ProgramId", ""),
                XmlDomUtils.longOf(programEl, "ContactlessTransactionLimit", 0),
                XmlDomUtils.byteOf(programEl, "ContactlessTransactionLimitSupported", 0),
                XmlDomUtils.longOf(programEl, "ContactlessCVMLimit", 0),
                XmlDomUtils.byteOf(programEl, "CVMLimitSupported", 0),
                XmlDomUtils.longOf(programEl, "TerminalFloorLimit", 0),
                XmlDomUtils.byteOf(programEl, "TerminalFloorLimitSupported", 0),
                XmlDomUtils.byteOf(programEl, "StatusCheckSupported", 0),
                XmlDomUtils.byteOf(programEl, "ZeroAmountNoAllowed", 0),
                XmlDomUtils.byteOf(programEl, "CryptogramVersion17Supported", 0),
                XmlDomUtils.text(programEl, "ReaderTTQ"));
    }

    // ─── Amex ExpressPay ───────────────────────────────────────────────────

    private static AmexParamBean parseAmex(Element expressPayEl) {
        Element config = XmlDomUtils.firstChild(expressPayEl, "EXPRESSPAYCONFIGURATION");

        Element aidList = XmlDomUtils.firstChild(expressPayEl, "AIDLIST");
        List<AmexAidBean> aids = new ArrayList<>();
        for (Element aidEl : XmlDomUtils.children(aidList, "AID")) {
            aids.add(parseAmexAid(aidEl, config));
        }

        Element drlList = XmlDomUtils.firstChild(expressPayEl, "EXPRESSPAYDRLLIST");
        List<AmexDrlBean> programs = new ArrayList<>();
        for (Element drlEl : XmlDomUtils.children(drlList, "EXPRESSPAYDRL")) {
            programs.add(parseAmexDrl(drlEl));
        }

        AmexParamBean bean = new AmexParamBean();
        bean.setAid(aids);
        bean.setProgramID(programs);
        return bean;
    }

    private static AmexAidBean parseAmexAid(Element aidEl, @Nullable Element config) {
        return new AmexAidBean(
                null,
                XmlDomUtils.text(aidEl, "LocalAIDName", ""),
                XmlDomUtils.text(aidEl, "ApplicationID", ""),
                XmlDomUtils.byteOf(aidEl, "PartialAIDSelection", 0),
                XmlDomUtils.longOf(aidEl, "ContactlessTransactionLimit", 0),
                flagFor(aidEl, "ContactlessTransactionLimit"),
                XmlDomUtils.longOf(aidEl, "ContactlessFloorLimit", 0),
                XmlDomUtils.byteOf(aidEl, "ContactlessFloorLimitCheck", 0),
                XmlDomUtils.longOf(aidEl, "ContactlessCVMLimit", 0),
                flagFor(aidEl, "ContactlessCVMLimit"),
                XmlDomUtils.text(aidEl, "TACDenial"),
                XmlDomUtils.text(aidEl, "TACOnline"),
                XmlDomUtils.text(aidEl, "TACDefault"),
                null, // acquirerId — no source tag
                XmlDomUtils.text(aidEl, "DDOL"),
                XmlDomUtils.text(aidEl, "TDOL"),
                XmlDomUtils.text(aidEl, "TerminalAIDVersion"),
                // Shared across every AID in this scheme — from EXPRESSPAYCONFIGURATION.
                XmlDomUtils.text(config, "TerminalType"),
                XmlDomUtils.text(config, "TerminalCapability"),
                XmlDomUtils.text(config, "TerminalAdditionalCapability"),
                XmlDomUtils.text(config, "TerminalTransactionCapability"),
                XmlDomUtils.text(config, "UnpredictableNumberRange"),
                (byte) hexToInt(XmlDomUtils.text(config, "TerminalSupportOptimizationModeTransaction", "0")),
                XmlDomUtils.byteOf(config, "DelayAuthorizationSupport", 0),
                // Per-AID — tag 9F6D, distinct from the shared TerminalCapability above.
                XmlDomUtils.text(aidEl, "ExpresspayTerminalCapabilities"),
                null, // exFunction — no source tag
                (byte) 0, // supportFullOnline
                null); // aucRFU — no source tag
    }

    private static AmexDrlBean parseAmexDrl(Element drlEl) {
        return new AmexDrlBean(
                XmlDomUtils.text(drlEl, "ProgramId", ""),
                XmlDomUtils.longOf(drlEl, "RdClssTxnLmt", 0),
                XmlDomUtils.byteOf(drlEl, "RdClssTxnLmtFlg", 0),
                XmlDomUtils.longOf(drlEl, "RdCVMLmt", 0),
                XmlDomUtils.byteOf(drlEl, "RdCVMLmtFlg", 0),
                XmlDomUtils.longOf(drlEl, "RdClssFloorLmt", 0),
                XmlDomUtils.byteOf(drlEl, "RdClssFloorLmtFlg", 0),
                XmlDomUtils.byteOf(drlEl, "StatusCheckFlg", 0),
                XmlDomUtils.byteOf(drlEl, "AmtZeroNoAllowed", 0),
                (byte) hexToInt(XmlDomUtils.text(drlEl, "DynaLmicLimitSet", "0")));
    }

    /** No explicit enable-flag tag exists alongside these limits — treat "value present" as "on". */
    private static byte flagFor(Element parent, String valueTag) {
        return XmlDomUtils.text(parent, valueTag) == null ? (byte) 0 : (byte) 1;
    }

    private static int hexToInt(String hex) {
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
