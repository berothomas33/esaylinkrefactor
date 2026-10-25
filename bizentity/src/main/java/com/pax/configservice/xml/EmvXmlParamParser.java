package com.pax.configservice.xml;

import com.pax.bizentity.entity.CapkParamBean;
import com.pax.bizentity.entity.CapkRevokeBean;
import com.pax.bizentity.entity.EmvAid;
import com.pax.bizentity.entity.EmvCapk;
import com.pax.commonlib.utils.LogUtils;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import androidx.annotation.Nullable;

public final class EmvXmlParamParser {

    private static final String TAG = "EmvXmlParamParser";

    private static final boolean DEFAULT_ONLINE_PIN = true;
    private static final boolean DEFAULT_RAND_TRANS_SEL = false;
    private static final boolean DEFAULT_VELOCITY_CHECK = false;
    private static final int DEFAULT_FLOOR_LIMIT_CHECK_FLG = 1;
    private static final int DEFAULT_PRIORITY = 0;

    private EmvXmlParamParser() {
    }

    public static final class Result {
        public final List<EmvAid> aids;
        public final CapkParamBean capk;
        public final List<String> sections;

        Result(List<EmvAid> aids, CapkParamBean capk, List<String> sections) {
            this.aids = aids;
            this.capk = capk;
            this.sections = sections;
        }
    }

    public static Result parse(InputStream in)
            throws ParserConfigurationException, IOException, SAXException {
        Element root = XmlDomUtils.parseDocument(in).getDocumentElement();

        Element icsConfiguration = XmlDomUtils.firstChild(root, "ICSCONFIGURATION");
        Element cardSchemeConfiguration = XmlDomUtils.firstChild(root, "CARDSCHEMECONFIGRATION");
        List<Element> cardSchemes = XmlDomUtils.children(cardSchemeConfiguration, "CARDSCHEME");

        Element aidList = XmlDomUtils.firstChild(root, "AIDLIST");
        List<Element> aidElements = XmlDomUtils.children(aidList, "AID");
        List<Element> icsPerAid = new ArrayList<>();
        for (Element aidEl : aidElements) {
            icsPerAid.add(resolveIcs(XmlDomUtils.text(aidEl, "ApplicationID", ""), icsConfiguration, cardSchemes));
        }
        Element fallbackIcs = fallbackIcs(icsPerAid, icsConfiguration);

        List<EmvAid> aids = new ArrayList<>();
        for (int i = 0; i < aidElements.size(); i++) {
            Element aidEl = aidElements.get(i);
            Element ics = icsPerAid.get(i);
            if (ics == null) {
                ics = fallbackIcs;
                LogUtils.w(TAG, "AID " + XmlDomUtils.text(aidEl, "ApplicationID", "")
                        + " has no CARDSCHEMECONFIGRATION entry — using ICS profile "
                        + XmlDomUtils.text(ics, "Type"));
            }
            aids.add(parseAid(aidEl, ics));
        }

        CapkParamBean capk = parseCapk(root);
        return new Result(aids, capk, XmlDomUtils.childNames(root));
    }

    private static EmvAid parseAid(Element aidEl, @Nullable Element ics) {
        String aid = XmlDomUtils.text(aidEl, "ApplicationID", "");

        String cardDataInput = XmlDomUtils.text(ics, "CardDataInputCapability", "");
        String cvmCapability = XmlDomUtils.text(ics, "CVMCapability", "");
        String securityCapability = XmlDomUtils.text(ics, "SecurityCapability", "");
        String terminalCapability = cardDataInput + cvmCapability + securityCapability;

        return new EmvAid(
                null,
                XmlDomUtils.text(aidEl, "LocalAIDName", ""),
                aid,
                XmlDomUtils.intOf(aidEl, "PartialAIDSelection", EmvAid.PART_MATCH),
                DEFAULT_PRIORITY,
                DEFAULT_ONLINE_PIN,
                XmlDomUtils.intOf(aidEl, "TargetPercentage", 0),
                XmlDomUtils.intOf(aidEl, "MaxTargetPercentage", 0),
                DEFAULT_FLOOR_LIMIT_CHECK_FLG,
                DEFAULT_RAND_TRANS_SEL,
                DEFAULT_VELOCITY_CHECK,
                XmlDomUtils.longOf(aidEl, "FloorLimit", 0),
                XmlDomUtils.longOf(aidEl, "Threshold", 0),
                XmlDomUtils.text(aidEl, "TACDenial"),
                XmlDomUtils.text(aidEl, "TACOnline"),
                XmlDomUtils.text(aidEl, "TACDefault"),
                null,
                XmlDomUtils.text(aidEl, "TerminalDefaultDDOL"),
                XmlDomUtils.text(aidEl, "TerminalDefaultTDOL"),
                XmlDomUtils.text(aidEl, "TerminalAIDVersion"),
                XmlDomUtils.text(aidEl, "TerminalRiskManagementData"),
                terminalCapability,
                XmlDomUtils.text(ics, "AdditionalTerminalCapabilities", ""),
                XmlDomUtils.text(ics, "TerminalType", ""),
                XmlDomUtils.byteOf(ics, "GetDataForPINTryCounter", 1),
                XmlDomUtils.byteOf(ics, "BypassPINEntry", 1),
                XmlDomUtils.byteOf(ics, "SubsequentBypassPINEntry", 0),
                XmlDomUtils.byteOf(ics, "ForcedOnlineCapability", 0));
    }

    @Nullable
    private static Element resolveIcs(String aid, Element icsConfiguration, List<Element> cardSchemes) {
        String rid = aid.length() >= 10 ? aid.substring(0, 10) : aid;
        String icsType = null;
        String ridLevelIcsType = null;
        for (Element scheme : cardSchemes) {
            String schemeAid = XmlDomUtils.text(scheme, "AID", "");
            String schemeRid = XmlDomUtils.text(scheme, "RID", "");
            if (!schemeAid.isEmpty() && schemeAid.equals(aid)) {
                icsType = XmlDomUtils.text(scheme, "ICSTYPE");
                break;
            }
            if (schemeAid.isEmpty() && !schemeRid.isEmpty() && schemeRid.equals(rid)) {
                ridLevelIcsType = XmlDomUtils.text(scheme, "ICSTYPE");
            }
        }
        if (icsType == null) {
            icsType = ridLevelIcsType;
        }
        if (icsType == null) {
            return null;
        }
        for (Element ics : XmlDomUtils.children(icsConfiguration, "ICS")) {
            if (icsType.equals(XmlDomUtils.text(ics, "Type"))) {
                return ics;
            }
        }
        return null;
    }

    @Nullable
    static Element fallbackIcs(List<Element> icsPerAid, @Nullable Element icsConfiguration) {
        Map<Element, Integer> uses = new IdentityHashMap<>();
        Element best = null;
        int bestUses = 0;
        for (Element ics : icsPerAid) {
            if (ics == null) {
                continue;
            }
            int n = uses.containsKey(ics) ? uses.get(ics) + 1 : 1;
            uses.put(ics, n);
            if (n > bestUses) {
                best = ics;
                bestUses = n;
            }
        }
        return best != null ? best : XmlDomUtils.firstChild(icsConfiguration, "ICS");
    }

    private static CapkParamBean parseCapk(Element root) {
        Element capkList = XmlDomUtils.firstChild(root, "CAPKLIST");
        List<EmvCapk> capks = new ArrayList<>();
        for (Element capkEl : XmlDomUtils.children(capkList, "CAPK")) {
            String rid = XmlDomUtils.text(capkEl, "RID", "");
            int keyId = strictHexToInt(XmlDomUtils.text(capkEl, "KeyID", ""));
            if (rid.isEmpty() || keyId < 0) {
                LogUtils.w(TAG, "CAPK without a valid RID/KeyID (RID '" + rid + "') — skipped");
                continue;
            }
            capks.add(new EmvCapk(
                    null,
                    rid,
                    keyId,
                    hexToInt(XmlDomUtils.text(capkEl, "HashArithmeticIndex", "0")),
                    hexToInt(XmlDomUtils.text(capkEl, "RSAArithmeticIndex", "0")),
                    XmlDomUtils.text(capkEl, "Module"),
                    XmlDomUtils.text(capkEl, "Exponent"),
                    XmlDomUtils.text(capkEl, "ExpireDate"),
                    XmlDomUtils.text(capkEl, "CheckSum")));
        }

        Element revocationList = XmlDomUtils.firstChild(root, "REVOCATIONLIST");
        List<CapkRevokeBean> revoked = new ArrayList<>();
        for (Element revokedEl : XmlDomUtils.children(revocationList, "REVOKEDCERTIFICATE")) {
            String rid = XmlDomUtils.text(revokedEl, "RID", "");
            String keyId = XmlDomUtils.text(revokedEl, "KeyID", "");
            String serial = XmlDomUtils.text(revokedEl, "CertificateSN", "");
            if (rid.isEmpty() || strictHexToInt(keyId) < 0 || serial.isEmpty()) {
                LogUtils.w(TAG, "Revoked certificate without RID/KeyID/CertificateSN (RID '" + rid
                        + "') — skipped");
                continue;
            }
            revoked.add(new CapkRevokeBean(null, rid, keyId, serial));
        }

        CapkParamBean bean = new CapkParamBean();
        bean.setCapkList(capks);
        bean.setCapkRevokeList(revoked);
        return bean;
    }

    private static int hexToInt(String hex) {
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            XmlDomUtils.notANumber("hex", hex, 0);
            return 0;
        }
    }

    private static int strictHexToInt(String hex) {
        try {
            int value = Integer.parseInt(hex, 16);
            return value >= 0 && value <= 0xFF ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
