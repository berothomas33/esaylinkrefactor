package com.pax.configservice.xml;

import androidx.annotation.Nullable;
import com.alibaba.fastjson.TypeReference;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;
import com.pax.bizentity.entity.clss.paypass.PayPassParamBean;
import com.pax.commonlib.json.JsonProxy;
import com.pax.commonlib.utils.LogUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills the PayPass (Mastercard kernel C-2) settings a host {@code clss_param.clss} XML has no tag
 * for, from the bundled {@code paypass.json} — the values the terminal ran with before the XML
 * path existed. {@link ClssXmlParamParser} leaves them unset, and the kernel then runs without
 * them, e.g.:
 * <ul>
 *   <li>Kernel Configuration {@code DF811B} empty → "On-device cardholder verification" off, so a
 *       phone's "See Phone" (AAC + PCII {@code DF4B}) is reported as a plain decline;</li>
 *   <li>Terminal Type {@code 9F35} sent to the card as {@code FF};</li>
 *   <li>CVM Capability {@code DF8118}/{@code DF8119}, Card Data Input {@code DF8117}, Security
 *       Capability {@code DF811F}, Kernel ID, UDOL, torn-transaction limits... all empty.</li>
 * </ul>
 * Applied when the XML is stored ({@code EmvParamUpdater}) and again when PayPass parameters are
 * loaded for the kernel ({@code EmvParamService}), so parameters stored before this fix are
 * covered without a re-download. Anything the XML does carry is kept as is; only blanks are
 * filled. Each XML AID takes its
 * values from the bundled AID that matches it best (exact, else longest common prefix — e.g.
 * {@code A0000000041010} for Mastercard, {@code A0000000043060} for Maestro).
 */
public final class PayPassXmlDefaults {

    private static final String TAG = "PayPassXmlDefaults";

    private PayPassXmlDefaults() {
    }

    /**
     * Fills blanks in each of {@code aids} from the best-matching entry of {@code bundled}.
     *
     * @return the entries that changed — for writing back to GreenDAO, so the stored rows hold
     *     exactly what the kernel is given
     */
    public static List<PayPassAidBean> fill(@Nullable List<PayPassAidBean> aids,
            @Nullable List<PayPassAidBean> bundled) {
        List<PayPassAidBean> changed = new ArrayList<>();
        if (aids == null || bundled == null || bundled.isEmpty()) {
            return changed;
        }
        for (PayPassAidBean aid : aids) {
            PayPassAidBean defaults = bestMatch(aid.getAid(), bundled);
            if (defaults != null && fill(aid, defaults)) {
                changed.add(aid);
            }
        }
        return changed;
    }

    /** @return whether anything was filled */
    static boolean fill(PayPassAidBean aid, PayPassAidBean d) {
        // An entry from the host XML is recognisable by its missing Kernel Configuration (the
        // XML has no tag for it); one loaded from paypass.json always has it.
        boolean fromXml = blank(aid.getKernelConfig());
        boolean filled = false;
        if (blank(aid.getAcquirerId()) && !blank(d.getAcquirerId())) {
            aid.setAcquirerId(d.getAcquirerId());
            filled = true;
        }
        if (blank(aid.getVersion()) && !blank(d.getVersion())) {
            aid.setVersion(d.getVersion());
            filled = true;
        }
        if (blank(aid.getRiskManageData()) && !blank(d.getRiskManageData())) {
            aid.setRiskManageData(d.getRiskManageData());
            filled = true;
        }
        if (blank(aid.getTerminalType()) && !blank(d.getTerminalType())) {
            aid.setTerminalType(d.getTerminalType());
            filled = true;
        }
        if (blank(aid.getTerminalAdditionalCapability()) && !blank(d.getTerminalAdditionalCapability())) {
            aid.setTerminalAdditionalCapability(d.getTerminalAdditionalCapability());
            filled = true;
        }
        if (blank(aid.getKernelConfig()) && !blank(d.getKernelConfig())) {
            aid.setKernelConfig(d.getKernelConfig());
            filled = true;
        }
        if (blank(aid.getCardDataInput()) && !blank(d.getCardDataInput())) {
            aid.setCardDataInput(d.getCardDataInput());
            filled = true;
        }
        if (blank(aid.getCvmRequired()) && !blank(d.getCvmRequired())) {
            aid.setCvmRequired(d.getCvmRequired());
            filled = true;
        }
        if (blank(aid.getNoCvmRequired()) && !blank(d.getNoCvmRequired())) {
            aid.setNoCvmRequired(d.getNoCvmRequired());
            filled = true;
        }
        if (blank(aid.getSecurityCapability()) && !blank(d.getSecurityCapability())) {
            aid.setSecurityCapability(d.getSecurityCapability());
            filled = true;
        }
        if (blank(aid.getMagVersion()) && !blank(d.getMagVersion())) {
            aid.setMagVersion(d.getMagVersion());
            filled = true;
        }
        if (blank(aid.getMagCvm()) && !blank(d.getMagCvm())) {
            aid.setMagCvm(d.getMagCvm());
            filled = true;
        }
        if (blank(aid.getMagNoCvm()) && !blank(d.getMagNoCvm())) {
            aid.setMagNoCvm(d.getMagNoCvm());
            filled = true;
        }
        if (blank(aid.getKernelId()) && !blank(d.getKernelId())) {
            aid.setKernelId(d.getKernelId());
            filled = true;
        }
        if (blank(aid.getTlvParam()) && !blank(d.getTlvParam())) {
            aid.setTlvParam(d.getTlvParam());
            filled = true;
        }
        if (blank(aid.getDefaultUDOL()) && !blank(d.getDefaultUDOL())) {
            aid.setDefaultUDOL(d.getDefaultUDOL());
            filled = true;
        }
        if (blank(aid.getRefundVoidTacDenial()) && !blank(d.getRefundVoidTacDenial())) {
            aid.setRefundVoidTacDenial(d.getRefundVoidTacDenial());
            filled = true;
        }
        if (blank(aid.getMaxTornNum()) && !blank(d.getMaxTornNum())) {
            aid.setMaxTornNum(d.getMaxTornNum());
            filled = true;
        }
        if (blank(aid.getMaxTornLifetime()) && !blank(d.getMaxTornLifetime())) {
            aid.setMaxTornLifetime(d.getMaxTornLifetime());
            filled = true;
        }
        if (blank(aid.getDeviceSN()) && !blank(d.getDeviceSN())) {
            aid.setDeviceSN(d.getDeviceSN());
            filled = true;
        }
        if (blank(aid.getDsOperatorId()) && !blank(d.getDsOperatorId())) {
            aid.setDsOperatorId(d.getDsOperatorId());
            filled = true;
        }
        if (fromXml) {
            // The XML has no tag for these two either — ClssXmlParamParser hardcodes them — so
            // the bundled values (what ran before) are the real configuration, not the XML's.
            aid.setDataExchangeSupportFlag(d.getDataExchangeSupportFlag());
            aid.setSupportDefaultMcTermParam(d.getSupportDefaultMcTermParam());
            filled = true;
        }
        return filled;
    }

    /** The PayPass AIDs bundled in {@code paypass.json} — what {@code ConfigInit} loads. */
    @Nullable
    public static List<PayPassAidBean> loadBundled() {
        try {
            PayPassParamBean bundled = JsonProxy.getInstance().readObjFromAsset("paypass.json",
                    new TypeReference<PayPassParamBean>() { }.getType());
            return bundled != null ? bundled.getAid() : null;
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to read bundled paypass.json — PayPass kernel settings stay unset", e);
            return null;
        }
    }

    /** The bundled entry for {@code aid}: exact match, else the longest common AID prefix. */
    @Nullable
    static PayPassAidBean bestMatch(@Nullable String aid, List<PayPassAidBean> bundled) {
        String target = aid == null ? "" : aid.toUpperCase();
        PayPassAidBean best = null;
        int bestLength = -1;
        for (PayPassAidBean candidate : bundled) {
            String other = candidate.getAid() == null ? "" : candidate.getAid().toUpperCase();
            if (other.equals(target)) {
                return candidate;
            }
            int common = commonPrefix(target, other);
            if (common > bestLength) {
                bestLength = common;
                best = candidate;
            }
        }
        return best;
    }

    private static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    private static boolean blank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}
