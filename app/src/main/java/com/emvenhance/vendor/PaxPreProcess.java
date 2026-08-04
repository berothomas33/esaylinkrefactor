package com.emvenhance.vendor;

import com.pax.bizentity.entity.SearchMode;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.impl.EmvParamService;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvservice.emv.contact.EmvContactService;
import com.pax.emvservice.emv.contactless.ContactlessService;

/**
 * Builds an {@link EmvProcessParam} from application-level transaction parameters and
 * runs the PAX contact and/or contactless preTransProcess.
 *
 * <p>Package-private — only used by {@link PaxEmvBehavior#prepareKernel}.
 * Replaces the former {@code EmvPreProcessFacade} from the deleted {@code :emvflow} module.
 */
final class PaxPreProcess {

    private static final String TAG = "PaxPreProcess";

    private final String procCode;
    private final long amount;
    private final String timestamp;       // yyyyMMddHHmmss
    private final long amountOther;
    private final byte requestedSearchMode;

    private final EmvParamService paramService;
    private final EmvContactService contactService;
    private final ContactlessService contactlessService;

    PaxPreProcess(String procCode, long amount, String timestamp, long amountOther,
            byte requestedSearchMode,
            EmvParamService paramService,
            EmvContactService contactService,
            ContactlessService contactlessService) {
        this.procCode = procCode;
        this.amount = amount;
        this.timestamp = timestamp;
        this.amountOther = amountOther;
        this.requestedSearchMode = requestedSearchMode;
        this.paramService = paramService;
        this.contactService = contactService;
        this.contactlessService = contactlessService;
    }

    /**
     * Runs preTransProcess for contact and/or contactless kernels. Returns the
     * adjusted search-mode byte (some modes may be disabled by the kernel).
     *
     * @return adjusted search-mode bitmask; 0 if every mode was disabled
     */
    byte start() {
        EmvProcessParam emvProcessParam = buildProcessParam();
        byte adjusted = requestedSearchMode;

        if (SearchMode.isSupportIcc(requestedSearchMode)) {
            int ret = contactService.preTransProcess(emvProcessParam);
            LogUtils.d(TAG, "contact preTransProcess ret=" + ret);
            if (ret != 0) {
                adjusted &= ~SearchMode.INSERT;
            }
        }

        if (SearchMode.isSupportInternalPicc(requestedSearchMode)) {
            int ret = contactlessService.preTransProcess(emvProcessParam);
            LogUtils.d(TAG, "contactless preTransProcess ret=" + ret);
            if (ret != 0) {
                adjusted &= ~SearchMode.INTERNAL_WAVE;
            }
        }

        return adjusted;
    }

    private EmvProcessParam buildProcessParam() {
        // Parse timestamp: yyyyMMddHHmmss → BCD date (YYMMDD) + BCD time (HHMMSS)
        byte[] dateBytes = new byte[3];
        byte[] timeBytes = new byte[3];
        if (timestamp != null && timestamp.length() >= 14) {
            String dateStr = timestamp.substring(2, 8);   // YYMMDD
            String timeStr = timestamp.substring(8, 14);  // HHMMSS
            dateBytes = ConvertUtils.strToBcdPaddingLeft(dateStr);
            timeBytes = ConvertUtils.strToBcdPaddingLeft(timeStr);
        }

        byte transType = 0;
        if (procCode != null && procCode.length() >= 2) {
            try {
                transType = (byte) Integer.parseInt(procCode.substring(0, 2));
            } catch (NumberFormatException ignored) { }
        }

        EmvTransParam transParam = new EmvTransParam.Builder()
                .setAmount(amount)
                .setAmountOther(amountOther)
                .setTransType(transType)
                .setTransDate(dateBytes)
                .setTransTime(timeBytes)
                .setFlowType(EmvTransParam.FLOWTYPE_COMPLETE)
                .create();

        // Get cached config (AID/CAPK/kernel params) then rebuild with transParam included
        EmvProcessParam cached = paramService.getCachedEmvParam();
        return new EmvProcessParam.Builder(transParam, cached.getTermConfig(),
                cached.getCapkParam())
                .setEmvAidList(cached.getEmvAidList())
                .setPayWaveParam(cached.getPayWaveParam())
                .setPassParam(cached.getPayPassParam())
                .setDpasParam(cached.getDpasParam())
                .setAmexParam(cached.getAmexParam())
                .setEFTParam(cached.getEftParam())
                .setJcbParam(cached.getJcbParam())
                .setMirParam(cached.getMirParam())
                .setPbocParam(cached.getPbocParam())
                .setPureParam(cached.getPureParam())
                .setRuPayParam(cached.getRuPayParam())
                .create();
    }
}
