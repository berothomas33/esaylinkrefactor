package com.pax.configservice.xml;

import com.pax.bizentity.entity.CapkParamBean;
import com.pax.bizentity.entity.CapkRevokeBean;
import com.pax.bizentity.entity.EmvAid;
import com.pax.bizentity.entity.EmvCapk;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import androidx.annotation.Nullable;

/**
 * Parses a PAX {@code emv_param.emv} (contact) parameter file — the same XML shape as
 * {@code EMVPARAM/AIDLIST + EMVPARAM/CAPKLIST + EMVPARAM/REVOCATIONLIST}, produced into the same
 * {@link EmvAid}/{@link CapkParamBean} shapes {@code ConfigInit} currently loads from the bundled
 * {@code contactAid.json}/{@code capk.json} — so a downloaded param file feeds the exact same
 * {@code EmvParamService} insert methods a first-boot asset load does.
 *
 * <p><b>No reference parser (PAX's own "EMVParamsEngine") was available to build this against</b>
 * — it's reconstructed from the XML itself and the target entities' field names/doc comments.
 * Two things are worth flagging:
 * <ul>
 *   <li>{@code TerminalType}/{@code CardDataInputCapability}/{@code CVMCapability}/
 *       {@code SecurityCapability}/{@code AdditionalTerminalCapabilities}/PIN-bypass fields live
 *       in {@code ICSCONFIGURATION} (one profile per {@code Type}), and {@code AID} entries don't
 *       carry them directly — {@code CARDSCHEMECONFIGRATION} maps each RID or AID to an ICS
 *       profile by name. This parser resolves that join (see {@link #resolveIcs}) to flatten
 *       those fields onto each {@link EmvAid}, matching how {@code contactAid.json} already
 *       carries them per-entry rather than as a separate profile table.
 *   <li>{@link EmvAid} has no XML source at all for {@code priority}/{@code onlinePin}/
 *       {@code randTransSel}/{@code velocityCheck}/{@code floorLimitCheckFlg}/{@code acquirerId}
 *       — no tag in this file carries them. Defaults below were chosen deliberately (see
 *       {@link #DEFAULT_ONLINE_PIN} etc.), not left at Java's zero-value by accident — review
 *       them against real terminal risk-management requirements before relying on this for a
 *       live update.
 * </ul>
 */
public final class EmvXmlParamParser {

    /** No per-AID PIN-capability tag in this file — default to supporting online PIN (matches
     * the bundled contactAid.json's own primary entries) rather than silently disabling it. */
    private static final boolean DEFAULT_ONLINE_PIN = true;
    /** No per-AID risk-management flags in this file — default both off (fewer forced-online
     * transactions) rather than guess at issuer-specific risk parameters. */
    private static final boolean DEFAULT_RAND_TRANS_SEL = false;
    private static final boolean DEFAULT_VELOCITY_CHECK = false;
    /** Floor-limit checking is a required EMV Book 3 risk control — default it enabled. */
    private static final int DEFAULT_FLOOR_LIMIT_CHECK_FLG = 1;
    private static final int DEFAULT_PRIORITY = 0;

    private EmvXmlParamParser() {
    }

    public static final class Result {
        public final List<EmvAid> aids;
        public final CapkParamBean capk;

        Result(List<EmvAid> aids, CapkParamBean capk) {
            this.aids = aids;
            this.capk = capk;
        }
    }

    public static Result parse(InputStream in)
            throws ParserConfigurationException, IOException, SAXException {
        Element root = XmlDomUtils.parseDocument(in).getDocumentElement();

        Element icsConfiguration = XmlDomUtils.firstChild(root, "ICSCONFIGURATION");
        Element cardSchemeConfiguration = XmlDomUtils.firstChild(root, "CARDSCHEMECONFIGRATION");
        List<Element> cardSchemes = XmlDomUtils.children(cardSchemeConfiguration, "CARDSCHEME");

        Element aidList = XmlDomUtils.firstChild(root, "AIDLIST");
        List<EmvAid> aids = new ArrayList<>();
        for (Element aidEl : XmlDomUtils.children(aidList, "AID")) {
            aids.add(parseAid(aidEl, icsConfiguration, cardSchemes));
        }

        CapkParamBean capk = parseCapk(root);
        return new Result(aids, capk);
    }

    private static EmvAid parseAid(Element aidEl, Element icsConfiguration, List<Element> cardSchemes) {
        String aid = XmlDomUtils.text(aidEl, "ApplicationID", "");
        Element ics = resolveIcs(aid, icsConfiguration, cardSchemes);

        String cardDataInput = XmlDomUtils.text(ics, "CardDataInputCapability", "");
        String cvmCapability = XmlDomUtils.text(ics, "CVMCapability", "");
        String securityCapability = XmlDomUtils.text(ics, "SecurityCapability", "");
        // Tag 9F33 (Terminal Capabilities) is these three ICS bytes concatenated, in order.
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
                null, // acquirerId — no source tag in this file
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

    /**
     * {@code CARDSCHEMECONFIGRATION} maps either a whole RID or one specific AID to an
     * {@code ICSTYPE} profile name; an AID-specific rule takes precedence over its RID's rule.
     * {@code ICSCONFIGURATION} then holds one {@code <ICS>} per profile {@code Type}.
     */
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

    private static CapkParamBean parseCapk(Element root) {
        Element capkList = XmlDomUtils.firstChild(root, "CAPKLIST");
        List<EmvCapk> capks = new ArrayList<>();
        for (Element capkEl : XmlDomUtils.children(capkList, "CAPK")) {
            capks.add(new EmvCapk(
                    null,
                    XmlDomUtils.text(capkEl, "RID", ""),
                    hexToInt(XmlDomUtils.text(capkEl, "KeyID", "0")),
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
            revoked.add(new CapkRevokeBean(
                    null,
                    XmlDomUtils.text(revokedEl, "RID", ""),
                    XmlDomUtils.text(revokedEl, "KeyID", ""),
                    XmlDomUtils.text(revokedEl, "CertificateSN", "")));
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
            return 0;
        }
    }
}
