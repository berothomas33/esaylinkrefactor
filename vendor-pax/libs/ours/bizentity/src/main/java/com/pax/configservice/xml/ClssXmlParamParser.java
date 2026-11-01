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
        public final List<String> sections;

        Result(@Nullable PayPassParamBean payPass, @Nullable PayWaveParamBean payWave,
                @Nullable AmexParamBean amex, List<String> sections) {
            this.payPass = payPass;
            this.payWave = payWave;
            this.amex = amex;
            this.sections = sections;
        }
    }

    public static Result parse(InputStream in)
            throws ParserConfigurationException, IOException, SAXException {
        return parse(in, null);
    }

    public static Result parse(InputStream in, @Nullable TerminalWideValues terminalWide)
            throws ParserConfigurationException, IOException, SAXException {
        Element root = XmlDomUtils.parseDocument(in).getDocumentElement();

        Element payPassEl = XmlDomUtils.firstChild(root, "PAYPASSPARAM");
        Element payWaveEl = XmlDomUtils.firstChild(root, "PAYWAVEPARAM");
        Element expressPayEl = XmlDomUtils.firstChild(root, "EXPRESSPAYPARAM");

        Result result = new Result(
                payPassEl == null ? null : parsePayPass(payPassEl),
                payWaveEl == null ? null : parsePayWave(payWaveEl),
                expressPayEl == null ? null : parseAmex(expressPayEl),
                XmlDomUtils.childNames(root));
        if (terminalWide != null) {
            applyTerminalWide(result, terminalWide);
        }
        return result;
    }

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

    private static PayPassParamBean parsePayPass(Element payPassEl) {
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
                null,
                XmlDomUtils.text(aidEl, "TerminalAIDVersion"),
                XmlDomUtils.text(aidEl, "TerminalRisk"),
                XmlDomUtils.text(config, "TerminalType"),
                XmlDomUtils.text(config, "AdditionalTerminalCapability"),
                XmlDomUtils.text(config, "KernelConfiguration"),
                XmlDomUtils.text(config, "CardDataInput"),
                XmlDomUtils.text(config, "CVMCapability_CVMRequired"),
                XmlDomUtils.text(config, "CVMCapability_NoCVMRequired"),
                XmlDomUtils.text(config, "SecurityCapability"),
                XmlDomUtils.text(aidEl, "MagneticApplicationVersionNumber"),
                XmlDomUtils.text(config, "MagneticCVM"),
                magNoCvm(config),
                XmlDomUtils.text(config, "KernelID"),
                null,
                (byte) 0,
                null,
                null,
                0L,
                null,
                true,
                hexByte(XmlDomUtils.text(config, "MaximumTornNumber"), 1),
                hexByte(XmlDomUtils.text(config, "TornLeftTime"), 2),
                null,
                null,
                XmlDomUtils.text(config, "MobileSupport"),
                accountType(config));
    }

    private static PayWaveParamBean parsePayWave(Element payWaveEl) {
        Element capabilities = XmlDomUtils.firstChild(payWaveEl, "TERMINALCAPABILITIES");
        String terminalType = XmlDomUtils.text(capabilities, "TermType");
        String securityCapability = XmlDomUtils.text(capabilities, "OffDataAuth");
        String terminalCapability = terminalCapability(capabilities);
        Element aidList = XmlDomUtils.firstChild(payWaveEl, "AIDLIST");
        List<PaywaveAidBean> aids = new ArrayList<>();
        for (Element aidEl : XmlDomUtils.children(aidList, "AID")) {
            PaywaveAidBean aid = parsePayWaveAid(aidEl);
            aid.setTerminalType(terminalType);
            aid.setSecurityCapability(securityCapability);
            aid.setTerminalCapability(terminalCapability);
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

    @Nullable
    static String terminalCapability(@Nullable Element capabilities) {
        String cardDataInput = XmlDomUtils.text(capabilities, "CardDataInput");
        String cvmSupport = XmlDomUtils.text(capabilities, "CVMSupport");
        String offDataAuth = XmlDomUtils.text(capabilities, "OffDataAuth");
        if (blank(cardDataInput) || blank(cvmSupport) || blank(offDataAuth)) {
            return null;
        }
        return hexByte(cardDataInput, 1) + hexByte(cvmSupport, 1) + hexByte(offDataAuth, 1);
    }

    private static PaywaveAidBean parsePayWaveAid(Element aidEl) {
        return new PaywaveAidBean(
                null,
                XmlDomUtils.text(aidEl, "LocalAIDName", ""),
                XmlDomUtils.text(aidEl, "ApplicationID", ""),
                XmlDomUtils.byteOf(aidEl, "PartialAIDSelection", 0),
                XmlDomUtils.text(aidEl, "TerminalAIDVersion"),
                null,
                XmlDomUtils.text(aidEl, "ReaderTTQ"),
                XmlDomUtils.byteOf(aidEl, "CryptogramVersion17Supported", 0),
                XmlDomUtils.byteOf(aidEl, "StatusCheckSupported", 0),
                XmlDomUtils.byteOf(aidEl, "ZeroAmountNoAllowed", 0),
                null,
                (byte) 0,
                (byte) 0,
                null,
                null);
    }

    private static PayWaveInterFloorLimitBean parsePayWaveFloorLimit(Element entry) {
        boolean contactlessFloor = XmlDomUtils.text(entry, "ContactlessFloorLimit") != null;
        return new PayWaveInterFloorLimitBean(
                (byte) hexToInt(XmlDomUtils.text(entry, "TransactionType", "00")),
                XmlDomUtils.longOf(entry,
                        contactlessFloor ? "ContactlessFloorLimit" : "TerminalFloorLimit", 0),
                XmlDomUtils.longOf(entry, "ContactlessTransactionLimit", 0),
                XmlDomUtils.longOf(entry, "ContactlessCVMLimit", 0),
                XmlDomUtils.byteOf(entry, "ContactlessTransactionLimitSupported", 0),
                XmlDomUtils.byteOf(entry, "CVMLimitSupported", 0),
                XmlDomUtils.byteOf(entry, contactlessFloor
                        ? "ContactlessFloorLimitSupported" : "TerminalFloorLimitSupported", 0));
    }

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
                null,
                XmlDomUtils.text(aidEl, "DDOL"),
                XmlDomUtils.text(aidEl, "TDOL"),
                XmlDomUtils.text(aidEl, "TerminalAIDVersion"),
                XmlDomUtils.text(config, "TerminalType"),
                XmlDomUtils.text(config, "TerminalCapability"),
                XmlDomUtils.text(config, "TerminalAdditionalCapability"),
                XmlDomUtils.text(config, "TerminalTransactionCapability"),
                XmlDomUtils.text(config, "UnpredictableNumberRange"),
                (byte) hexToInt(XmlDomUtils.text(config, "TerminalSupportOptimizationModeTransaction", "0")),
                XmlDomUtils.byteOf(config, "DelayAuthorizationSupport", 0),
                XmlDomUtils.text(aidEl, "ExpresspayTerminalCapabilities"),
                null,
                (byte) 0,
                null);
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

    @Nullable
    private static String accountType(@Nullable Element config) {
        String value = XmlDomUtils.text(config, "AccoutType");
        return value != null ? value : XmlDomUtils.text(config, "AccountType");
    }

    @Nullable
    private static String magNoCvm(@Nullable Element config) {
        String value = XmlDomUtils.text(config, "MageticNoCVM");
        return value != null ? value : XmlDomUtils.text(config, "MagneticNoCVM");
    }

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

    private static byte flagFor(Element parent, String valueTag) {
        return XmlDomUtils.text(parent, valueTag) == null ? (byte) 0 : (byte) 1;
    }

    private static int hexToInt(String hex) {
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            XmlDomUtils.notANumber("hex", hex, 0);
            return 0;
        }
    }
}
