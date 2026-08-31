/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2020-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                  Author	                 Action
 * 20200525  	         JackHuang               Create
 * ===========================================================================================
 */
package com.pax.emvlib.process.contactless;

import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.process.EmvBase;
import com.pax.emvbase.process.contactless.IContactlessCallback;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvlib.base.contactless.ClssKernelProcess;
import com.pax.jemv.clcommon.ByteArray;
import com.pax.jemv.clcommon.Clss_PreProcInterInfo;
import com.pax.jemv.clcommon.Clss_TransParam;
import com.pax.jemv.clcommon.KernType;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.entrypoint.api.ClssEntryApi;
import java.util.Arrays;

/**
 * this is clss process framework, Please do not change this file at will, if need to
 * add a new kernel process,Please add aid in ClssEntryAddAid.java and create a ClssXXXProcess.java
 * extend ClssKernelProcessFactory.java. Then create ClssXXXProcess object in ClssKernelProcessFactory.java
 */
public class ClssProcess extends EmvBase {
    private static final String TAG = "ClssProcess";
    private EmvProcessParam emvProcessParam;
    private Clss_TransParam transParam;
    private KernType kernType;
    private ClssKernelProcess clssKernelProcess = null;
    private ClssEntryAddAid clssEntryAddAid;
    private static ClssProcess instance;
    private long startMs;
    private long finishMs;

    private IContactlessCallback clssStatusListener;

    private ClssProcess() {
    }

    public static ClssProcess getInstance() {
        if (instance == null) {
            instance = new ClssProcess();
        }
        return instance;
    }

    /**
     * 1.entry init
     * 2.add aid
     *
     * @param emvParam
     */
    @Override
    public int preTransProcess(EmvProcessParam emvParam) {
        this.emvProcessParam = emvParam;
        clssEntryAddAid = new ClssEntryAddAid(emvProcessParam);
        int ret = ClssEntryApi.Clss_CoreInit_Entry();
        if (ret != RetCode.EMV_OK) {
            LogUtils.e(TAG, "Clss_CoreInit_Entry ret = " + ret);
            return ret;
        }
        ClssEntryApi.Clss_DelAllAidList_Entry();
        ClssEntryApi.Clss_DelAllPreProcInfo();

        //if want to support other kernerl aid, Pls add in Class ClssEntryAddAid.
        clssEntryAddAid.addApp();
        //This function must be called before Clss_PreTransProc_Entry and only if entry library is used for PayPass application
        ClssEntryApi.Clss_SetMCVersion_Entry((byte) 0x03);
        return ClssEntryApi.Clss_PreTransProc_Entry(convertToClssTransParam());
    }

    /**
     * EMV "Application Selection" (contactless): builds the candidate list, final-selects one
     * application, and constructs the matching {@link ClssKernelProcess} for it via
     * {@link ClssKernelProcessFactory} — but does not run it. A candidate that fails final
     * selection (RSP_ERR / APP_BLOCK / ICC_BLOCK / RESELECT_APP) is deleted and the next one
     * tried, exactly like the original single-method flow did, until one succeeds or the
     * candidate list is exhausted. Kernel-internal — {@code Clss_GetPreProcInterFlg_Entry} and
     * {@code Clss_GetFinalSelectData_Entry} are plain data fetches feeding the kernel build, not
     * separate EMV steps.
     */
    public TransResult selectApplication() {
        if (clssKernelProcess == null) {
            // Only stamp the transaction's start once — startTransProcess() re-calls this on a
            // CLSS_RESELECT_APP retry, which must not reset the "time-consuming" measurement.
            startMs = System.currentTimeMillis();
        }
        int ret = ClssEntryApi.Clss_AppSlt_Entry(0, 0);
        if (ret != RetCode.EMV_OK) {
            LogUtils.e(TAG, "Clss_AppSlt_Entry ret = " + ret);
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        kernType = new KernType();
        ByteArray daArray = new ByteArray();
        while (true) {
            /*
             * If EMV_RSP_ERR || EMV_APP_BLOCK || ICC_BLOCK || CLSS_RESELECT_APP is returned,
             * application program shall call Clss_DelCurCandApp_Entry to delete current application from candidate list,
             * and select the next application by calling Clss_FinalSelect_Entry, if there are other applications in the candidate list*/
            ret = ClssEntryApi.Clss_FinalSelect_Entry(kernType, daArray);
            LogUtils.d(TAG, "Clss_FinalSelect_Entry ret = " + ret + ", Kernel Type = " + kernType.kernType);
            if (ret == RetCode.EMV_RSP_ERR || ret == RetCode.EMV_APP_BLOCK
                    || ret == RetCode.ICC_BLOCK || ret == RetCode.CLSS_RESELECT_APP) {
                ret = ClssEntryApi.Clss_DelCurCandApp_Entry();
                if (ret != RetCode.EMV_OK) {
                    //candidate list is empty, quit.
                    return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
                }
                continue;
            } else if (ret != RetCode.EMV_OK) {
                if (kernType.kernType == KernType.KERNTYPE_RUPAY) {
                    // RUPAY.QCLD.012
                    byte[] status = new byte[3];
                    int result = ClssEntryApi.Clss_GetExtendFunction_Entry(0x03, status, status.length);
                    LogUtils.d(TAG, "Clss_GetExtendFunction_Entry ret = " + result
                            + ", status = " + ConvertUtils.bcd2Str(status));
                    if (result == RetCode.EMV_OK) {
                        byte sw[] = Arrays.copyOfRange(status, 1, 3);
                        if (Arrays.equals(sw, new byte[]{(byte) 0x62, (byte) 0x83})) {
                            ret = RetCode.EMV_APP_BLOCK;
                        }
                    }
                }
                return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
            }

            Clss_PreProcInterInfo clssPreProcInterInfo = new Clss_PreProcInterInfo();
            ret = ClssEntryApi.Clss_GetPreProcInterFlg_Entry(clssPreProcInterInfo);
            if (ret != RetCode.EMV_OK) {
                LogUtils.e(TAG, "Clss_GetPreProcInterFlg_Entry ret = " + ret);
                return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
            }

            ByteArray finalSelectData = new ByteArray();
            ret = ClssEntryApi.Clss_GetFinalSelectData_Entry(finalSelectData);
            if (ret != RetCode.EMV_OK) {
                LogUtils.e(TAG, "Clss_GetFinalSelectData_Entry ret = " + ret);
                return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
            }

            clssKernelProcess = new ClssKernelProcessFactory(kernType.kernType)
                    .setEmvProcessParam(emvProcessParam)
                    .setClssTransParam(transParam)
                    .setFinalSelectData(finalSelectData.data, finalSelectData.length)
                    .setPreProcInterInfo(clssPreProcInterInfo)
                    .setClssStatusListener(clssStatusListener)
                    .build();

            return new TransResult(RetCode.EMV_OK);
        }
    }

    /** True if the selected kernel exposes read/offline-auth/restrictions as separate calls. */
    public boolean supportsGranularSteps() {
        return clssKernelProcess != null && clssKernelProcess.supportsGranularSteps();
    }

    /** EMV "Read Application Data" — only called when {@link #supportsGranularSteps()} is true. */
    public TransResult readApplicationData() {
        if (clssKernelProcess == null) {
            return noKernelSelectedResult("readApplicationData");
        }
        return clssKernelProcess.readApplicationData();
    }

    /** EMV "Offline Data Authentication" — only called when {@link #supportsGranularSteps()} is true. */
    public TransResult offlineDataAuthentication() {
        if (clssKernelProcess == null) {
            return noKernelSelectedResult("offlineDataAuthentication");
        }
        return clssKernelProcess.offlineDataAuthentication();
    }

    /** EMV "Processing Restrictions" — only called when {@link #supportsGranularSteps()} is true. */
    public TransResult processRestrictions() {
        if (clssKernelProcess == null) {
            return noKernelSelectedResult("processRestrictions");
        }
        return clssKernelProcess.processRestrictions();
    }

    /**
     * Fail closed instead of NPE-ing when a caller reaches one of the per-phase methods before
     * {@link #selectApplication()} has actually built a kernel — a caller-ordering bug, not a
     * card/kernel error, but the transaction must still end cleanly rather than crash.
     */
    private TransResult noKernelSelectedResult(String method) {
        LogUtils.e(TAG, method + ": no kernel selected — failing closed");
        return new TransResult(RetCode.EMV_DATA_ERR, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    /**
     * The remaining bundle after application selection (and, for a granular kernel, after read
     * data / offline data auth / processing restrictions too): cardholder verification through
     * the first GENERATE AC. {@code CLSS_RESELECT_APP} can only be discovered once a kernel
     * actually runs, so the reselect-and-retry loop lives here rather than in
     * {@link #selectApplication()} — a failing candidate is deleted and application selection is
     * re-run for the next one, exactly like the original single-method flow did.
     */
    @Override
    public TransResult startTransProcess() {
        if (clssKernelProcess == null) {
            return noKernelSelectedResult("startTransProcess");
        }
        while (true) {
            TransResult transResult = clssKernelProcess.startTransProcess();
            if (transResult.getResultCode() == RetCode.CLSS_RESELECT_APP) {
                int ret = ClssEntryApi.Clss_DelCurCandApp_Entry();
                if (ret != RetCode.EMV_OK) {
                    LogUtils.e(TAG, "Clss_DelCurCandApp_Entry ret = " + ret);
                    return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
                }
                TransResult reselect = selectApplication();
                if (reselect.getTransResult() != null) {
                    // selection itself ended terminally (e.g. candidate list exhausted)
                    return reselect;
                }
                continue;
            }

            finishMs = System.currentTimeMillis();
            LogUtils.d(TAG, "clss trans time-consuming ms = " + (finishMs - startMs));
            return transResult;
        }
    }

    /**
     * Process as below:
     * 1.Issuer Authentication
     * 2.Script Processing
     * 3.Complete Trans
     */
    @Override
    public TransResult completeTransProcess(IssuerRspData issuerRspData) {
        return clssKernelProcess.completeTransProcess(issuerRspData);
    }

    @Override
    public byte[] getTlv(int tag) {
        ByteArray value = new ByteArray();
        int ret = clssKernelProcess.getTlv(tag, value);
        if (ret != RetCode.EMV_OK){
            return new byte[0];
        }
        return Arrays.copyOf(value.data, value.length);
    }

    /**
     * Sets value on specific tag
     *
     * @param tag   emv tag
     * @param value tag value
     */
    @Override
    public void setTlv(int tag, byte[] value) {
        clssKernelProcess.setTlv(tag, value);
    }

    private Clss_TransParam convertToClssTransParam() {
        long amount = emvProcessParam.getEmvTransParam().getAmount();
        long amoutOther = emvProcessParam.getEmvTransParam().getAmountOther();
        long traceNo = emvProcessParam.getEmvTransParam().getTransTraceNo();
        byte transType = emvProcessParam.getEmvTransParam().getTransType();
        byte[] transDate = emvProcessParam.getEmvTransParam().getTransDate();
        byte[] transTime = emvProcessParam.getEmvTransParam().getTransTime();
        transParam = new Clss_TransParam(amount, amoutOther, traceNo, transType, transDate, transTime);
        return transParam;
    }

    public String getTrack2() {
        if (clssKernelProcess != null) {
            return clssKernelProcess.getTrack2();
        }

        return "";
    }

    public KernType getKernType() {
        return kernType;
    }

    public boolean isNeedSecondTap(IssuerRspData issuerRspData) {
        return clssKernelProcess.isNeedSecondTap(issuerRspData);
    }

    public void registerClssProcessListener(IContactlessCallback contactlessCallback) {
        this.clssStatusListener = contactlessCallback;
    }
    public void unregisterClssProcessListener() {
        this.clssStatusListener = null;
        if (clssKernelProcess != null){
            clssKernelProcess.setClssStatusListener(null);
        }
    }
}
