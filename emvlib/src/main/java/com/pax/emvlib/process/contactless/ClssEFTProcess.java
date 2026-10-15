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
 * 2021/06/15                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvlib.process.contactless;

import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.constant.TagsTable;
import com.pax.emvbase.param.clss.EFTParam;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvbase.utils.EmvDebugger;
import com.pax.emvlib.base.contactless.ClssKernelProcess;
import com.pax.emvlib.base.utils.EmvParamConvert;
import com.pax.jemv.clcommon.ACType;
import com.pax.jemv.clcommon.ByteArray;
import com.pax.jemv.clcommon.Clss_ReaderParam;
import com.pax.jemv.clcommon.CvmType;
import com.pax.jemv.clcommon.EMV_CAPK;
import com.pax.jemv.clcommon.EMV_REVOCLIST;
import com.pax.jemv.clcommon.OnlineResult;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.eftpos.api.ClssEFTPOSApi;
import com.pax.jemv.eftpos.model.Clss_EFTAidParam;

public class ClssEFTProcess extends ClssKernelProcess<EFTParam> {

    private static final String TAG = "ClssEFTProcess";

    /**
     * EFTPOS is the most separable of the ten contactless kernels — every EMV Book 3 phase
     * below has its own native call, so it doesn't need the shared atomic-kernel default.
     */
    @Override
    public boolean supportsGranularSteps() {
        return true;
    }

    /** EMV "Read Application Data" (kernel init/config + Initiate + Read Application Data). */
    @Override
    public TransResult readApplicationData() {
        // Kernel Initialization
        int ret = ClssEFTPOSApi.Clss_CoreInit_EFT();
        if (ret != RetCode.EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED,
                    CvmResultEnum.CVM_NO_CVM);
        }

        // Set Parameters
        ClssEFTPOSApi.Clss_SetReaderParam_EFT(toClssReaderParam());

        // Set final select data to kernel
        ClssEFTPOSApi.Clss_SetFinalSelectData_EFT(finalSelectData,finalSelectDataLen);

        // Set parameters correspond to AID
        ClssEFTPOSApi.Clss_SetAidParam_EFT(genEFTAidParam());

        // set terminal parameters into kernel which will send to backend later in field55
        setParam();

        // Set related parameters for EFTPOS transaction
        ClssEFTPOSApi.Clss_SetTransData_EFT(transParam,preProcInterInfo);

        // Initiate transaction processing
        ret = ClssEFTPOSApi.Clss_InitApp_EFT();
        if (ret != RetCode.EMV_OK){
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED,
                    CvmResultEnum.CVM_NO_CVM);
        }

        // Read application data
        ret = ClssEFTPOSApi.Clss_ReadAppData_EFT();
        if (ret != RetCode.EMV_OK){
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED,
                    CvmResultEnum.CVM_NO_CVM);
        }

        return new TransResult(RetCode.EMV_OK);
    }

    /** EMV "Offline Data Authentication" (pre-TAA, then CAPK/revocation list + card auth). */
    @Override
    public TransResult offlineDataAuthentication() {
        // Pre-terminal action analysis
        ACType preAcType = new ACType();
        int ret = ClssEFTPOSApi.Clss_PreTAAProc_EFT(preAcType);
        if (ret != RetCode.EMV_OK){
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED,
                    CvmResultEnum.CVM_NO_CVM);
        }

        // Remove card
        if (clssStatusListener != null) {
            clssStatusListener.onReadCardOk();
            clssStatusListener.onRemoveCard();
        }

        // Add a new CA public key
        ClssEFTPOSApi.Clss_DelAllCAPK_EFT();
        ClssEFTPOSApi.Clss_DelAllRevocList_EFT();
        addCapkRevList();

        // Offline data authentication
        ret = ClssEFTPOSApi.Clss_CardAuth_EFT();
        if (ret != RetCode.EMV_OK){
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED,
                    CvmResultEnum.CVM_NO_CVM);
        }

        return new TransResult(RetCode.EMV_OK);
    }

    /** EMV "Processing Restrictions". */
    @Override
    public TransResult processRestrictions() {
        ClssEFTPOSApi.Clss_ProcRestrict_EFT();
        return new TransResult(RetCode.EMV_OK);
    }

    /** Cardholder verification through the first GENERATE AC — see class doc on {@link ClssKernelProcess#startTransProcess()}. */
    @Override
    public TransResult startTransProcess() {
        // Cardholder verification
        CvmType cvmType = new CvmType();
        int ret = ClssEFTPOSApi.Clss_CVMProc_EFT(cvmType);
        if (ret != RetCode.EMV_OK){
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED,
                    CvmResultEnum.CVM_NO_CVM);
        }

        // Post-terminal action analysis
        ACType postAcType = new ACType();
        ret = ClssEFTPOSApi.Clss_PostTAAProc_EFT(postAcType);
        if (ret != RetCode.EMV_OK){
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED,
                    CvmResultEnum.CVM_NO_CVM);
        }

        TransResult result = new TransResult(RetCode.EMV_OK,
                TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        if (postAcType.type == ACType.AC_TC) {
            result.setTransResult(TransResultEnum.RESULT_OFFLINE_APPROVED);
        } else if (postAcType.type == ACType.AC_ARQC) {
            result.setTransResult(TransResultEnum.RESULT_REQ_ONLINE);
        } else {
            result.setTransResult(TransResultEnum.RESULT_OFFLINE_DENIED);
        }
        result.setCvmResult(EmvParamConvert.convertCVM(cvmType.type));
        return result;
    }

    private Clss_ReaderParam toClssReaderParam() {
        Clss_ReaderParam readerParam = new Clss_ReaderParam();

        readerParam.ulReferCurrCon = clssParam.getReferCurrCon();
        readerParam.aucMchNameLoc = clssParam.getMerchantNameLocation();
        readerParam.usMchLocLen = (short) readerParam.aucMchNameLoc.length;
        readerParam.aucMerchCatCode = clssParam.getMerchantCategoryCode();
        readerParam.aucMerchantID = clssParam.getMerchantId();
        readerParam.acquierId = clssParam.getAcquirerId();

        readerParam.aucTmID = clssParam.getTermId();
        readerParam.ucTmType = clssParam.getTermTypeForReader();
        readerParam.aucTmCap = clssParam.getTermCapability();
        readerParam.aucTmCapAd = clssParam.getExTermCapability();
        readerParam.aucTmCntrCode = clssParam.getCountryCode();
        readerParam.aucTmTransCur = clssParam.getTransCurrCode();
        readerParam.ucTmTransCurExp = clssParam.getTransCurrExpForReader();
        readerParam.aucTmRefCurCode = clssParam.getReferCurrCode();
        readerParam.ucTmRefCurExp = clssParam.getReferCurrExp();
        return readerParam;
    }

    private Clss_EFTAidParam genEFTAidParam() {
        Clss_EFTAidParam clss_eftAidParam = new Clss_EFTAidParam();
        clss_eftAidParam.TACDefault = clssParam.getTacDefault();
        clss_eftAidParam.TACDenial = clssParam.getTacDenial();
        clss_eftAidParam.TACOnline = clssParam.getTacOnline();
        clss_eftAidParam.Version = clssParam.getAppVersion();
        return clss_eftAidParam;
    }

    private void setParam() {
        setTlv(TagsTable.CURRENCY_CODE, clssParam.getTransCurrCode());
        setTlv(TagsTable.ACQUIRER_ID, null);
        setTlv(TagsTable.COUNTRY_CODE, clssParam.getCountryCode());
        setTlv(TagsTable.TERMINAL_TYPE, clssParam.getTermTypeBytes());

        setTlv(TagsTable.AMOUNT, clssParam.getAmount());
        setTlv(TagsTable.AMOUNT_OTHER, clssParam.getOtherAmount());
        setTlv(TagsTable.TRANS_TYPE, clssParam.getTransTypeBytes());  //trans type
        setTlv(TagsTable.TRANS_DATE, clssParam.getTransDate());  //trans date

        //region Conditional or Optional Parameters
        setTlv(TagsTable.TRANS_TIME, clssParam.getTransTime());  //trans time
        //trans number
        setTlv(TagsTable.TRNAS_NO, clssParam.getTransNo());                  //trans Number

        setTlv(0xDF810C, clssParam.getKernelId());
        setTlv(TagsTable.MERCHANT_CATEGORY_CODE, clssParam.getMerchantCategoryCode());
        setTlv(TagsTable.MERCHANT_ID, null);
        setTlv(TagsTable.TERMINAL_ID, null);
        setTlv(TagsTable.MERCHANT_NAME_LOCATION, null);

        //reader param
        setTlv(TagsTable.APP_VER, clssParam.getAppVersion());
        setTlv(TagsTable.TERMINAL_CAPABILITY, clssParam.getTermCapability());
        setTlv(TagsTable.TRANS_CURRENCY_EXPONENT, clssParam.getTransCurrExpBytes());

        //aid param
        setTlv(0xDF51, clssParam.getFloorLimitBytes());
        setTlv(0xDF53, clssParam.getTransLimitBytes());
        setTlv(0xDF54, clssParam.getTransLimitBytes());
        setTlv(0xDF52, clssParam.getCvmLimitBytes());
    }

    @Override
    public TransResult completeTransProcess(IssuerRspData issuerRspData) {
        // Please note that the OnlineResult passed in may be any value of 0, 1, or 3. But the EFT
        // kernel only supports 0 and 1.
        byte onlineResult = issuerRspData.getOnlineResult();
        if (onlineResult != OnlineResult.ONLINE_APPROVE) {
            onlineResult = OnlineResult.ONLINE_FAILED;
        }
        int ret = ClssEFTPOSApi.Clss_CompleteOnlineTrans_EFT(onlineResult);
        if (ret != RetCode.EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_ONLINE_CARD_DENIED,
                    CvmResultEnum.CVM_NO_CVM);
        }
        return new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_ONLINE_APPROVED, CvmResultEnum.CVM_NO_CVM);
    }

    @Override
    public int getTlv(int tag, ByteArray value) {
        int ret = ClssEFTPOSApi.Clss_GetTLVData_EFT((short) tag, value);
        EmvDebugger.d(TAG, "getTlv", tag, value);
        return ret;
    }

    @Override
    public int setTlv(int tag, byte[] value) {
        EmvDebugger.d(TAG, "setTlv", tag, value);
        if (value == null) {
            return ClssEFTPOSApi.Clss_SetTLVData_EFT((short) tag, null, 0);
        }
        return ClssEFTPOSApi.Clss_SetTLVData_EFT((short) tag, value, value.length);
    }

    @Override
    protected int addCapkAndRevokeList(EMV_CAPK emvCapk, EMV_REVOCLIST emvRevoclist) {
        int ret = RetCode.EMV_OK;
        if (emvCapk != null) {
            ClssEFTPOSApi.Clss_DelAllCAPK_EFT();
            ret = ClssEFTPOSApi.Clss_AddCAPK_EFT(emvCapk);
            if (ret != RetCode.EMV_OK) {
                LogUtils.e(TAG, "Clss_AddCAPK_MIR ret = " + ret);
                return ret;
            }
        }

        if (emvRevoclist != null) {
            ClssEFTPOSApi.Clss_DelAllRevocList_EFT();
            return ClssEFTPOSApi.Clss_AddRevocList_EFT(emvRevoclist);
        }
        return ret;
    }

    @Override
    public String getTrack2() {
        ByteArray track = new ByteArray();
        int ret = getTlv(TagsTable.TRACK2, track);
        if (ret == RetCode.EMV_OK) {
            return getTrack2FromTag57(ConvertUtils.bcd2Str(track.data, track.length));
        }
        return "";
    }

    @Override
    public boolean isNeedSecondTap(IssuerRspData issuerRspData) {
        return false;
    }
}
