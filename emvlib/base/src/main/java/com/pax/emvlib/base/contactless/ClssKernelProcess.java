/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2021-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                           Author                      Action
 * 2022/04/15                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvlib.base.contactless;

import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.BuildConfig;
import com.pax.emvbase.constant.TagsTable;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.clss.BaseAid;
import com.pax.emvbase.param.clss.BaseParam;
import com.pax.emvbase.param.common.Capk;
import com.pax.emvbase.param.common.CapkRevoke;
import com.pax.emvbase.process.contactless.IContactlessCallback;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvlib.base.utils.EmvParamConvert;
import com.pax.jemv.clcommon.ByteArray;
import com.pax.jemv.clcommon.Clss_PreProcInterInfo;
import com.pax.jemv.clcommon.Clss_TransParam;
import com.pax.jemv.clcommon.EMV_CAPK;
import com.pax.jemv.clcommon.EMV_REVOCLIST;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.clcommon.TransactionPath;
import java.util.Arrays;

/**
 * this is clss kernel process framework, Please do not change this file at will, if need to
 * add a new kernel process,Please add aid in ClssEntryAddAid.java and create a ClssXXXProcess.java
 * extend ClssKernelProcessFactory.java. Then create ClssXXXProcess object in ClssKernelProcessFactory.java
 */
public abstract class ClssKernelProcess<T extends BaseParam<? extends BaseAid>> {
    private static final String TAG = "ClssKernelProcess";
    protected EmvProcessParam emvProcessParam;
    protected Clss_TransParam transParam;
    protected T clssParam;
    protected byte[] finalSelectData;
    protected int finalSelectDataLen;
    protected Clss_PreProcInterInfo preProcInterInfo;
    protected TransactionPath transactionPath = new TransactionPath();
    protected IContactlessCallback clssStatusListener;

    protected static final boolean enableDebugLog = BuildConfig.DEBUG;

    /**
     * True if this brand's native SDK exposes read/offline-auth/restrictions as separate calls
     * (see {@link com.pax.emvlib.process.contactless.ClssEFTProcess},
     * {@link com.pax.emvlib.process.contactless.ClssPayPassProcess}). False (the default) means
     * the kernel's entire transaction runs inside one blocking {@link #startTransProcess()}
     * call — a genuine vendor SDK limitation, not an implementation gap.
     * {@code ClssProcess}/{@code PaxEmvBehavior} skip straight from application selection to
     * the {@link #startTransProcess()} bundle for these kernels; {@link #readApplicationData()},
     * {@link #offlineDataAuthentication()}, and {@link #processRestrictions()} are never called.
     */
    public boolean supportsGranularSteps() {
        return false;
    }

    /**
     * EMV "Read Application Data" (Initiate Application Processing + Read Application Data).
     * Only called when {@link #supportsGranularSteps()} is true — see its doc for kernels that
     * can't separate this from {@link #startTransProcess()}.
     */
    public TransResult readApplicationData() {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support granular steps");
    }

    /**
     * EMV "Offline Data Authentication" (SDA/DDA/CDA). Only called when
     * {@link #supportsGranularSteps()} is true.
     */
    public TransResult offlineDataAuthentication() {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support granular steps");
    }

    /**
     * EMV "Processing Restrictions". Only called when {@link #supportsGranularSteps()} is true.
     */
    public TransResult processRestrictions() {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support granular steps");
    }

    /**
     * 1.core init.
     * 2.different clss kernel's process is a little different
     *
     * <p>For a kernel that overrides {@link #readApplicationData()},
     * {@link #offlineDataAuthentication()}, and {@link #processRestrictions()} individually
     * (granular steps), this is what remains after those: cardholder verification through the
     * first GENERATE AC — the same bundle {@code ContactProcess#startTransProcess()} means for
     * the chip path. For a kernel that doesn't override them (the default, non-granular case),
     * this is the one call that runs the entire transaction.
     */
    public abstract TransResult startTransProcess();

    //if need second tap, call this
    public abstract TransResult completeTransProcess(IssuerRspData issuerRspData);

    public abstract int getTlv(int tag, ByteArray value);

    public abstract int setTlv(int tag, byte[] value);

    //add capk and revovk list
    protected abstract int addCapkAndRevokeList(EMV_CAPK emvCapk, EMV_REVOCLIST emvRevoclist);

    public abstract String getTrack2();

    public abstract boolean isNeedSecondTap(IssuerRspData issuerRspData);

    public ClssKernelProcess<T> setEmvProcessParam(EmvProcessParam emvProcessParam) {
        this.emvProcessParam = emvProcessParam;
        return this;
    }

    public ClssKernelProcess<T> setClssTransParam(Clss_TransParam transParam) {
        this.transParam = transParam;
        return this;
    }

    public ClssKernelProcess<T> setFinalSelectData(byte[] finalSelectData, int finalSelectDataLen) {
        this.finalSelectData = finalSelectData;
        this.finalSelectDataLen = finalSelectDataLen;
        return this;
    }


    public ClssKernelProcess<T> setPreProcInterInfo(Clss_PreProcInterInfo preProcInterInfo) {
        this.preProcInterInfo = preProcInterInfo;
        return this;
    }

    public ClssKernelProcess<T> setClssStatusListener(IContactlessCallback clssStatusListener) {
        this.clssStatusListener = clssStatusListener;
        return this;
    }

    public ClssKernelProcess<T> setClssParam(T clssParam) {
        this.clssParam = clssParam;
        return this;
    }

    protected int addCapkRevList() {
        ByteArray keyIdTLVDataList = new ByteArray(1);
        ByteArray aidTLVDataList = new ByteArray(17);
        if (getTlv(TagsTable.CAPK_ID, keyIdTLVDataList) == RetCode.EMV_OK &&
                getTlv(TagsTable.CAPK_RID, aidTLVDataList) == RetCode.EMV_OK) {
            byte keyId = keyIdTLVDataList.data[0];
            if (enableDebugLog) {
                LogUtils.d(TAG, "addCapkRevList keyId bcd: " + ConvertUtils.bcd2Str(new byte[]{keyId}));
            }
            byte[] rid = new byte[5];
            System.arraycopy(aidTLVDataList.data, 0, rid, 0, 5);
            EMV_CAPK emvCapk = null;
            EMV_REVOCLIST emvRevoclist = null;
            for (Capk capk : emvProcessParam.getCapkParam().getCapkList()) {
                if (Arrays.equals(capk.getRid(), rid) && capk.getKeyId() == keyId) {
                    emvCapk = EmvParamConvert.toEMVCapk(capk);
                }
            }
            for (CapkRevoke capkRevoke : emvProcessParam.getCapkParam().getCapkRevokeList()) {
                if (Arrays.equals(capkRevoke.getRid(), rid) && capkRevoke.getKeyId() == keyId) {
                    emvRevoclist = new EMV_REVOCLIST(rid, keyId, capkRevoke.getCertificateSN());
                }
            }

            return addCapkAndRevokeList(emvCapk, emvRevoclist);
        }

        return RetCode.EMV_DATA_ERR;
    }

    protected static String getTrack2FromTag57(String tag57) {
        return tag57.split("F")[0];
    }

}
