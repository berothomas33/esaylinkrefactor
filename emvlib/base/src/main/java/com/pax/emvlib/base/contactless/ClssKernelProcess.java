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
     * 1.core init.
     * 2.different clss kernel's process is a little different
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
