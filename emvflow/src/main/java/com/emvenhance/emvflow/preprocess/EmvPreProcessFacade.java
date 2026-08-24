package com.emvenhance.emvflow.preprocess;

import com.emvenhance.emvflow.device.EmvDeviceImpl;
import com.pax.bizentity.entity.SearchMode;
import com.pax.commonlib.currency.CurrencyConverter;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.export.IEmvParamService;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvlib.dpas.contact.ContactProcess;
import com.pax.emvlib.process.contactless.ClssProcess;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.device.DeviceManager;
import com.pax.poslib.gl.convert.ConvertHelper;
import com.pax.poslib.model.ModelInfo;

/**
 * Builds EMV process parameters and runs contact / contactless preTransProcess.
 *
 * <p>Dependencies are injected — no service locator.
 *
 * <p><b>Experiment branch:</b> {@code contact} is {@link ContactProcess} directly, not
 * {@code IEmvContactService} — the caller (PaxEmvBehavior.prepareKernel) is responsible for
 * handing in a freshly-built instance each transaction, since that reset used to happen inside
 * {@code EmvContactService.preTransProcess} and there's no service layer left to own it.
 * {@code contactless} is {@link ClssProcess} directly too, not {@code IEmvContactlessService} —
 * {@code ClssProcess} is a process-lifetime singleton on its own, so unlike {@code contact} there
 * was never a "fresh instance" to hand in.
 */
public class EmvPreProcessFacade {
    private static final String TAG = "EmvPreProcessFacade";

    private final String procCode;
    private final long amount;
    private final String dateTime;
    private final long traceNo;
    private byte searchCardMode;
    private final IEmvParamService paramService;
    private final ContactProcess contact;
    private final ClssProcess contactless;

    public EmvPreProcessFacade(String procCode, long amount, String dateTime, long traceNo,
            byte searchCardMode, IEmvParamService paramService, ContactProcess contact,
            ClssProcess contactless) {
        this.procCode = procCode;
        this.amount = amount;
        this.dateTime = dateTime;
        this.traceNo = traceNo;
        this.searchCardMode = searchCardMode;
        this.paramService = paramService;
        this.contact = contact;
        this.contactless = contactless;
    }

    public byte start() {
        DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
        byte[] proc = ConvertHelper.getConvert().strToBcdPaddingRight(this.procCode);
        if (!SearchMode.isSupportIcc(searchCardMode) && !SearchMode.isWave(searchCardMode)) {
            return 0;
        }
        if (paramService == null) {
            LogUtils.e(TAG, "EmvParamService missing");
            return searchCardMode;
        }
        EmvProcessParam cachedEmvParam = paramService.getCachedEmvParam();
        EmvTransParam.Builder builder = new EmvTransParam.Builder();
        builder.setTransType(proc[0])
                .setAmount(amount)
                .setAmountOther(0L)
                .setTerminalID(ModelInfo.getInstance().getSN().getBytes())
                .setTransCurrencyCode(CurrencyConverter.getCurrencyCode())
                .setTransCurrencyExponent((byte) CurrencyConverter.getDigitsNum())
                .setTransDate(ConvertUtils.strToBcdPaddingLeft(dateTime.substring(2, 8)))
                .setTransTime(ConvertUtils.strToBcdPaddingLeft(dateTime.substring(8)))
                .setTransTraceNo(Long.parseLong(ConvertUtils.getPaddedNumber(traceNo, 6)))
                .setFlowType(EmvTransParam.FLOWTYPE_COMPLETE)
                .setMaskPattern("")
                .setPinLenSet("0,4,5,6,7,8,9,10,11,12\0".getBytes())
                .setPciTimeout(60 * 1000);
        EmvProcessParam.Builder processParamBuilder = new EmvProcessParam.Builder()
                .setTermConfig(cachedEmvParam.getTermConfig())
                .setCapkParam(cachedEmvParam.getCapkParam());

        if (SearchMode.isSupportIcc(searchCardMode) && contact != null) {
            builder.setPciMode((byte) 1);
            processParamBuilder.setEmvTransParam(builder.create())
                    .setEmvAidList(cachedEmvParam.getEmvAidList());
            int contactRet = contact.preTransProcess(processParamBuilder.create());
            if (contactRet != RetCode.EMV_OK) {
                LogUtils.e(TAG, "contact pre process failed");
                searchCardMode = (byte) (searchCardMode & (~SearchMode.INSERT));
            }
        }
        if ((SearchMode.isSupportInternalPicc(searchCardMode)
                || SearchMode.isSupportExternalPicc(searchCardMode)) && contactless != null) {
            if (!SearchMode.isSupportIcc(searchCardMode)) {
                processParamBuilder.setEmvTransParam(builder.create());
            }
            processParamBuilder.setAmexParam(cachedEmvParam.getAmexParam())
                    .setPassParam(cachedEmvParam.getPayPassParam())
                    .setPayWaveParam(cachedEmvParam.getPayWaveParam())
                    .setDpasParam(cachedEmvParam.getDpasParam())
                    .setEFTParam(cachedEmvParam.getEftParam())
                    .setJcbParam(cachedEmvParam.getJcbParam())
                    .setMirParam(cachedEmvParam.getMirParam())
                    .setPbocParam(cachedEmvParam.getPbocParam())
                    .setPureParam(cachedEmvParam.getPureParam())
                    .setRuPayParam(cachedEmvParam.getRuPayParam());
            int contactlessRet = contactless.preTransProcess(processParamBuilder.create());
            if (contactlessRet != RetCode.EMV_OK) {
                LogUtils.e(TAG, "contactless pre process failed");
                searchCardMode = (byte) (searchCardMode & (~SearchMode.WAVE));
            }
        }
        return searchCardMode;
    }
}
