package com.pax.configservice.xml;

import com.pax.bizentity.entity.clss.amex.AmexAidBean;
import com.pax.bizentity.entity.clss.amex.AmexDrlBean;
import com.pax.bizentity.entity.clss.amex.AmexParamBean;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;
import com.pax.bizentity.entity.clss.paypass.PayPassParamBean;
import com.pax.bizentity.entity.clss.paywave.PayWaveInterFloorLimitBean;
import com.pax.bizentity.entity.clss.paywave.PayWaveParamBean;
import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean;
import com.pax.commonlib.utils.LogUtils;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
 * <p>PayPass and Amex each carry one shared configuration block ({@code PAYPASSCONFIGURATION} /
 * {@code EXPRESSPAYCONFIGURATION}: Kernel Configuration, Terminal Type, CVM capabilities...),
 * applied to every AID of that scheme.
 *
 * <p>Several bean fields (PayPass: {@code tlvParam}/{@code defaultUDOL}/{@code deviceSN}/
 * {@code dsOperatorId}/{@code acquirerId}/refund-void limits; Amex: {@code exFunction}/
 * {@code aucRFU}) have no corresponding tag anywhere in this file and are left unset — not
 * defaulted to a guessed value.
 */
public final class ClssXmlParamParser {

    private static final String TAG = "ClssXmlParamParser";

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
        return parse(in, null);
    }

    /**
     * @param terminalWide terminal-wide values from the contact parameters (Terminal Type,
     *     Additional Terminal Capabilities, Security Capability) — used for any contactless AID
     *     whose scheme block doesn't set them (PayWave has no such block at all). A scheme's own
     *     value always wins.
     */
    public static Result parse(InputStream in, @Nullable TerminalWideValues terminalWide)
            throws ParserConfigurationException, IOException, SAXException {
        Element root = XmlDomUtils.parseDocument(in).getDocumentElement();

        Element payPassEl = XmlDomUtils.firstChild(root, "PAYPASSPARAM");
        Element payWaveEl = XmlDomUtils.firstChild(root, "PAYWAVEPARAM");
        Element expressPayEl = XmlDomUtils.firstChild(root, "EXPRESSPAYPARAM");

        Result result = new Result(
                payPassEl == null ? null : parsePayPass(payPassEl),
                payWaveEl == null ? null : parsePayWave(payWaveEl),
                expressPayEl == null ? null : parseAmex(expressPayEl));
        if (terminalWide != null) {
            applyTerminalWide(result, terminalWide);
        }
        return result;
    }

    /** Fills terminal-wide values the scheme blocks left unset — see {@link TerminalWideValues}. */
    static void applyTerminalWide(Result result, TerminalWideValues tw) {
        if (result.payPass != null && result.payPass.getAid() != null) {
            for (PayPassAidBean aid : result.payPass.getAid()) {
                if (blank(aid.getTerminalType())) {
                    aid.setTerminalType(tw.terminalType(aid.getAid()));
                }
                if (blank(aid.getTerminalAdditionalCapability())) {
                    aid.setTerminalAdditionalCapability(tw.additionalCapability(aid.getAid()));
                }
                if (blank(aid.getSecurityCapability())) {
                    aid.setSecurityCapability(tw.securityCapability(aid.getAid()));
                }
            }
        }
        if (result.payWave != null && result.payWave.getAid() != null) {
            for (PaywaveAidBean aid : result.payWave.getAid()) {
                if (blank(aid.getTerminalType())) {
                    aid.setTerminalType(tw.terminalType(aid.getAid()));
                }
                if (blank(aid.getSecurityCapability())) {
                    aid.setSecurityCapability(tw.securityCapability(aid.getAid()));
                }
            }
        }
        if (result.amex != null && result.amex.getAid() != null) {
            for (AmexAidBean aid : result.amex.getAid()) {
                if (blank(aid.getTerminalType())) {
                    aid.setTerminalType(tw.terminalType(aid.getAid()));
                }
                if (blank(aid.getTerminalAdditionalCapability())) {
                    aid.setTerminalAdditionalCapability(tw.additionalCapability(aid.getAid()));
                }
            }
        }
    }

    /** Element children's tag names, for diagnosing an unexpected file structure. */
    private static String childNames(Element parent) {
        StringBuilder names = new StringBuilder();
        org.w3c.dom.NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(nodes.item(i).getNodeName());
            }
        }
        return names.toString();
    }

    private static boolean blank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    // ─── PayPass (Mastercard) ─────────────────────────────────────────────

    private static PayPassParamBean parsePayPass(Element payPassEl) {
        // Kernel settings shared by every PayPass AID — Kernel Configuration, Terminal Type, CVM
        // capabilities, Security Capability... Not reading this block left them all unset, so
        // the kernel ran with terminal type FF, no CVM support (online PIN skipped) and
        // On-device CVM off (See Phone reported as a decline).
        Element config = XmlDomUtils.firstChild(payPassEl, "PAYPASSCONFIGURATION");
        if (config == null) {
            LogUtils.w(TAG, "PAYPASSPARAM has no PAYPASSCONFIGURATION — PayPass kernel settings "
                    + "(Kernel Configuration, Terminal Type, CVM capabilities...) stay unset. "
                    + "PAYPASSPARAM children: " + childNames(payPassEl));
        } else {
            LogUtils.i(TAG, "PAYPASSCONFIGURATION: KernelConfiguration="
                    + XmlDomUtils.text(config, "KernelConfiguration")
                    + " TerminalType=" + XmlDomUtils.text(config, "TerminalType")
                    + " CVMCapability_CVMRequired=" + XmlDomUtils.text(config, "CVMCapability_CVMRequired")
                    + " CVMCapability_NoCVMRequired=" + XmlDomUtils.text(config, "CVMCapability_NoCVMRequired")
                    + " SecurityCapability=" + XmlDomUtils.text(config, "SecurityCapability")
                    + " MobileSupport=" + XmlDomUtils.text(config, "MobileSupport"));
        }
        Element aidList = XmlDomUtils.firstChild(payPassEl, "AIDLIST");
        List<PayPassAidBean> aids = new ArrayList<>();
        for (Element aidEl : XmlDomUtils.children(aidList, "AID")) {
            aids.add(parsePayPassAid(aidEl, config));
        }
        PayPassParamBean bean = new PayPassParamBean();
        bean.setAid(aids);
        return bean;
    }

    private static PayPassAidBean parsePayPassAid(Element aidEl, @Nullable Element config) {
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
                // Shared across every AID in this scheme — from PAYPASSCONFIGURATION.
                XmlDomUtils.text(config, "TerminalType"), // 9F35
                XmlDomUtils.text(config, "AdditionalTerminalCapability"), // 9F40
                XmlDomUtils.text(config, "KernelConfiguration"), // DF811B
                XmlDomUtils.text(config, "CardDataInput"), // DF8117
                XmlDomUtils.text(config, "CVMCapability_CVMRequired"), // DF8118
                XmlDomUtils.text(config, "CVMCapability_NoCVMRequired"), // DF8119
                XmlDomUtils.text(config, "SecurityCapability"), // DF811F
                XmlDomUtils.text(aidEl, "MagneticApplicationVersionNumber"), // 9F6D
                XmlDomUtils.text(config, "MagneticCVM"), // DF811E
                magNoCvm(config), // DF812C
                XmlDomUtils.text(config, "KernelID"), // DF810C
                null, // kernelIdBytes (@Transient)
                (byte) 0, // dataExchangeSupportFlag — no source tag
                null, // tlvParam — no source tag
                null, // defaultUDOL — no source tag
                0L, // refundVoidFloorLimit — no source tag
                null, // refundVoidTacDenial — no source tag
                true, // supportDefaultMcTermParam — matches PayPassAidBean's own field default
                hexByte(XmlDomUtils.text(config, "MaximumTornNumber"), 1), // DF811D
                hexByte(XmlDomUtils.text(config, "TornLeftTime"), 2), // DF811C
                null, // deviceSN — no source tag
                null, // dsOperatorId — no source tag
                XmlDomUtils.text(config, "MobileSupport"), // 9F7E
                accountType(config)); // 5F57
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

        PayWaveParamBean bean = new PayWaveParamBean();
        bean.setAid(aids);
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
                (byte) 0, // enDDAVerNo
                null); // interWareFloorLimit — set by parsePayWave from INTERWARELIST
    }

    private static PayWaveInterFloorLimitBean parsePayWaveFloorLimit(Element entry) {
        return new PayWaveInterFloorLimitBean(
                (byte) hexToInt(XmlDomUtils.text(entry, "TransactionType", "00")),
                XmlDomUtils.longOf(entry, "TerminalFloorLimit", 0),
                XmlDomUtils.longOf(entry, "ContactlessTransactionLimit", 0),
                XmlDomUtils.longOf(entry, "ContactlessCVMLimit", 0),
                XmlDomUtils.byteOf(entry, "ContactlessTransactionLimitSupported", 0),
                XmlDomUtils.byteOf(entry, "CVMLimitSupported", 0),
                XmlDomUtils.byteOf(entry, "TerminalFloorLimitSupported", 0));
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

    /** Account Type — the host's files spell the tag "AccoutType". */
    @Nullable
    private static String accountType(@Nullable Element config) {
        String value = XmlDomUtils.text(config, "AccoutType");
        return value != null ? value : XmlDomUtils.text(config, "AccountType");
    }

    /** Mag-stripe "No CVM Required" capability — the host's files spell the tag "MageticNoCVM". */
    @Nullable
    private static String magNoCvm(@Nullable Element config) {
        String value = XmlDomUtils.text(config, "MageticNoCVM");
        return value != null ? value : XmlDomUtils.text(config, "MagneticNoCVM");
    }

    /**
     * A numeric value as a {@code bytes}-long hex string, left-padded — e.g. MaximumTornNumber
     * "2" → "02" (DF811D is 1 byte), TornLeftTime "06" → "0006" (DF811C is 2 bytes). {@code null}
     * stays {@code null}.
     */
    @Nullable
    static String hexByte(@Nullable String value, int bytes) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String hex = value.trim().toUpperCase();
        StringBuilder padded = new StringBuilder();
        for (int i = hex.length(); i < bytes * 2; i++) {
            padded.append('0');
        }
        return padded.append(hex).toString();
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
