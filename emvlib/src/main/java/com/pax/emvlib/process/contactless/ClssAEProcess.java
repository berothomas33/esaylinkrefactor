/*
 * ===========================================================================================
 *            COPYRIGHT
 *           PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *    This software is supplied under the terms of a license agreement or nondisclosure
 *    agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *    disclosed except in accordance with the terms in that agreement.
 *      Copyright (C) 2020-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 *  Description: // Detail description about the function of this module,
 *              // interfaces with the other modules, and dependencies.
 *  Revision History:
 *  Date                  Author	                 Action
 *   20201207  	           John Cai                 Create
 * ===========================================================================================
 */

package com.pax.emvlib.process.contactless;

import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.param.clss.AmexParam;
import com.pax.emvbase.param.clss.ExPayDRL;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvbase.utils.EmvDebugger;
import com.pax.emvlib.base.consts.EmvKernelConst;
import com.pax.emvlib.base.contactless.ClssKernelProcess;
import com.pax.jemv.amex.api.ClssAmexApi;
import com.pax.jemv.amex.model.CLSS_AEAIDPARAM;
import com.pax.jemv.amex.model.Clss_AddReaderParam_AE;
import com.pax.jemv.amex.model.Clss_ReaderParam_AE;
import com.pax.jemv.amex.model.ONLINE_PARAM;
import com.pax.jemv.amex.model.TransactionMode;
import com.pax.jemv.clcommon.ACType;
import com.pax.jemv.clcommon.ByteArray;
import com.pax.jemv.clcommon.Clss_ProgramID_II;
import com.pax.jemv.clcommon.Clss_ReaderParam;
import com.pax.jemv.clcommon.CvmType;
import com.pax.jemv.clcommon.EMV_CAPK;
import com.pax.jemv.clcommon.EMV_REVOCLIST;
import com.pax.jemv.clcommon.OnlineResult;
import com.pax.jemv.clcommon.RetCode;
import com.sankuai.waimai.router.annotation.RouterService;
import java.util.Arrays;
import java.util.List;

import static com.pax.jemv.clcommon.RetCode.EMV_OK;

@RouterService(interfaces = ClssKernelProcess.class, key = EmvKernelConst.AMEX)
public class ClssAEProcess extends ClssKernelProcess<AmexParam> {
    private static final String TAG = "ClssAEProcess";
    private TransactionMode transMode;

    private int init() {
        int ret;
        if (clssStatusListener == null || !clssStatusListener.needSeePhone()) {
            ret = ClssAmexApi.Clss_CoreInit_AE();
            if (ret != EMV_OK) {
                LogUtils.e(TAG, "Clss_CoreInit_AE ret = " + ret);
                return ret;
            }
        }

        ret = ClssAmexApi.Clss_SetExtendFunction_AE(clssParam.getExFunction());
        if (ret != EMV_OK) {
            LogUtils.e(TAG, "Clss_SetExtendFunction_AE ret = " + ret);
            return ret;
        }

        return ret;
    }

    private int setReaderParam() {
        //Set reader params
        int ret;
        Clss_ReaderParam_AE readerParamAe = new Clss_ReaderParam_AE();
        readerParamAe.aucUNRange = clssParam.getUnpredictableNumberRange();
        readerParamAe.ucTmSupOptTrans = clssParam.getSupportOptTrans();

        Clss_ReaderParam readerParam = new Clss_ReaderParam();

        readerParam.aucTmCap = clssParam.getTermCapability();
        readerParam.aucTmCapAd = clssParam.getExTermCapability();
        readerParam.ucTmType = clssParam.getTermType();
        readerParam.acquierId = clssParam.getAcquirerId();

        readerParam.aucMchNameLoc = clssParam.getMerchantNameLocation();
        readerParam.usMchLocLen = (short) readerParam.aucMchNameLoc.length;
        readerParam.aucMerchantID = clssParam.getMerchantId();
        readerParam.aucMerchCatCode = clssParam.getMerchantCategoryCode();
        readerParam.aucTmCntrCode = clssParam.getCountryCode();
        readerParam.aucTmRefCurCode = clssParam.getReferCurrCode();
        readerParam.ucTmRefCurExp = clssParam.getReferCurrExp();
        readerParam.ulReferCurrCon = clssParam.getReferCurrCon();

        readerParam.aucTmID = clssParam.getTermId();
        readerParam.aucTmTransCur = clssParam.getTransCurrCode();
        readerParam.ucTmTransCurExp = clssParam.getTransCurrExp();
        readerParamAe.stReaderParam = readerParam;
        ret = ClssAmexApi.Clss_SetReaderParam_AE(readerParamAe);
        if (ret != EMV_OK) {
            LogUtils.e(TAG, "Clss_SetReaderParam_AE ret = " + ret);
        }

        Clss_AddReaderParam_AE addReaderParamAe = new Clss_AddReaderParam_AE(clssParam.getTermTransCap(),
                clssParam.getDelayAuthSupport(), clssParam.getAucRFU());
        ret = ClssAmexApi.Clss_SetAddReaderParam_AE(addReaderParamAe);
        if (ret != EMV_OK) {
            LogUtils.e(TAG, "Clss_SetReaderParam_AE ret = " + ret);
        }
        return ret;
    }

    private int setTransData() {
        int ret;

        CLSS_AEAIDPARAM aeaidparam = new CLSS_AEAIDPARAM();
        aeaidparam.dDOL = clssParam.getdDOL();
        aeaidparam.FloorLimit = clssParam.getClssFloorLimit();
        aeaidparam.FloorLimitCheck = clssParam.getClssFloorLimitCheck();
        aeaidparam.TACDefault = clssParam.getTacDefault();
        aeaidparam.TACDenial = clssParam.getTacDenial();
        aeaidparam.TACOnline = clssParam.getTacOnline();
        aeaidparam.ucAETermCap = clssParam.getExPayRdCap();
        aeaidparam.tDOL = clssParam.gettDOL();
        aeaidparam.Version = clssParam.getAppVersion();
        aeaidparam.AcquierId = clssParam.getAcquirerId();
        ret = ClssAmexApi.Clss_SetAEAidParam_AE(aeaidparam);
        if (ret != EMV_OK) {
            LogUtils.e(TAG, "Clss_SetAEAidParam_AE ret = " + ret);
            return ret;
        }

        ret = ClssAmexApi.Clss_SetFinalSelectData_AE(finalSelectData, finalSelectDataLen);
        if (ret != EMV_OK) {
            LogUtils.e(TAG, "Clss_SetFinalSelectData_AE ret = " + ret);
            return ret;
        }

        ret = ClssAmexApi.Clss_SetTransData_AE(transParam, preProcInterInfo);
        if (ret != EMV_OK) {
            LogUtils.e(TAG, "Clss_SetTransData_AE ret = " + ret);
            return ret;
        }

        return ret;
    }

    private int addDRL() {
        int ret = EMV_OK;
        List<ExPayDRL> drls = clssParam.getAmexDRLs();
        if (drls == null) {
            return EMV_OK;
        }
        for (ExPayDRL drl : drls) {
            Clss_ProgramID_II programID = new Clss_ProgramID_II();
            programID.aucProgramId = drl.getProgramId();
            programID.aucRdClssFLmt = drl.getRdClssFloorLmt();
            programID.aucRdClssTxnLmt = drl.getRdClssTxnLmt();
            programID.aucRdCVMLmt = drl.getRdCVMLmt();
            programID.aucTermFLmt = drl.getTermFloorLmt();
            programID.ucAmtZeroNoAllowed = drl.getAmtZeroNoAllowed();
            programID.ucDynamicLimitSet = drl.getDynamicLimitSet();
            programID.ucRdClssFLmtFlg = drl.getRdClssFloorLmtFlg();
            programID.ucRdClssTxnLmtFlg = drl.getRdClssTxnLmtFlg();
            programID.ucRdCVMLmtFlg = drl.getRdCVMLmtFlg();
            programID.ucPrgramIdLen = drl.getProgramIdLen();
            programID.ucStatusCheckFlg = drl.getStatusCheckFlg();
            programID.ucTermFLmtFlg = drl.getTermFloorLmtFlg();
            ret = ClssAmexApi.Clss_AddDRL_AE(programID);
            if (ret != EMV_OK) {
                LogUtils.e(TAG, "Clss_AddDRL_AE ret = " + ret);
                return ret;
            }
        }
        return ret;
    }

    @Override
    public TransResult startTransProcess() {
        //initCore
        int ret = init();
        if (ret != EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        //read param
        ret = setReaderParam();
        if (ret != EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        //set transaction data
        ret = setTransData();
        if (ret != EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        //GPO process
        transMode = new TransactionMode();
        ret = ClssAmexApi.Clss_Proctrans_AE(transMode);
        if (ret != EMV_OK) {
            LogUtils.e(TAG, "Clss_Proctrans_AE ret = " + ret);
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        //read record
        ret = ClssAmexApi.Clss_ReadRecord_AE(new ByteArray());
        if (ret != EMV_OK) {
            LogUtils.e(TAG, "Clss_ReadRecord_AE ret = " + ret);
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        //add capk and revoc list
        ret = addCapkRevList();
        if (ret != EMV_OK) {
            LogUtils.e(TAG, "addCapkRevList ret = " + ret);
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        ret = ClssAmexApi.Clss_CardAuth_AE();
        if (ret != EMV_OK) {
            LogUtils.e(TAG, "Clss_CardAuth_AE ret = " + ret);
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        ret = addDRL();
        if (ret != EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        ACType acType = new ACType();
        ret = startTransaction(acType);
        if (ret != EMV_OK) {
            if (ret == RetCode.CLSS_REFER_CONSUMER_DEVICE) {
                return new TransResult(ret, TransResultEnum.RESULT_CLSS_SEE_PHONE, CvmResultEnum.CVM_CONSUMER_DEVICE);
            }
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        if (clssStatusListener != null) {
            clssStatusListener.onReadCardOk();
            clssStatusListener.onRemoveCard();
        }

        return processCVM(acType);
    }

    private int startTransaction(ACType acType) {
        int ret;
        ByteArray adviceFlg = new ByteArray();
        ByteArray onlineFlg = new ByteArray();
        ret = ClssAmexApi.Clss_StartTrans_AE(clssParam.getSupportFullOnline(), adviceFlg, onlineFlg);
        acType.type = ACType.AC_AAC;
        boolean isOnline = onlineFlg.data[0] == 1;
        LogUtils.e(TAG, "StartTrans ret = " + ret);
        if (enableDebugLog) {
            int ret1 = ClssAmexApi.Clss_GetDebugInfo_AE();
            LogUtils.e(TAG, "Start trans debug info: " + ret1);
            LogUtils.e(TAG, "advice: " + ConvertUtils.bcd2Str(Arrays.copyOfRange(
                    adviceFlg.data, 0, adviceFlg.length)) + ", online: " + onlineFlg.data[0]);
        }

//        if (ret == RetCode.CLSS_REFER_CONSUMER_DEVICE) {
//            clssStatusListener.onRemoveCard();
//            return ret;
//        } else
        if (ret == EMV_OK && isOnline) {
            acType.type = ACType.AC_ARQC;
        } else if (ret == EMV_OK) {
            acType.type = ACType.AC_TC;
            return ret;
        } else {
            clssStatusListener.onRemoveCard();
            return ret;
        }

        //if transaction needs to go online, check the delayed authorisation flag
        Clss_AddReaderParam_AE param = new Clss_AddReaderParam_AE(new byte[4], (byte) 0, new byte[27]);
        ret = ClssAmexApi.Clss_GetAddReaderParam_AE(param);
        if (ret != EMV_OK) {
            return ret;
        }
        if (param.ucDelayAuthFlag == 1) {
            ONLINE_PARAM onlineParam = new ONLINE_PARAM();
            onlineParam.aucRspCode = "00".getBytes();
            ret = ClssAmexApi.Clss_CompleteTrans_AE((byte) OnlineResult.ONLINE_APPROVE,
                    (byte) TransactionMode.AE_DELAYAUTH_PARTIALONLINE, onlineParam, adviceFlg);
            if (ret != EMV_OK) {
                LogUtils.e(TAG, "Delayed Auth failed, Clss_CompleteTrans_AE ret: " + ret);
                return ret;
            }
        }

        return ret;
    }

    private TransResult processCVM(ACType acType) {
        TransResult result = new TransResult(EMV_OK, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        byte cvmType = ClssAmexApi.Clss_GetCvmType_AE();
        LogUtils.w(TAG, "cvmType=" + cvmType);
        if (acType.type == ACType.AC_AAC) {
            result.setTransResult(TransResultEnum.RESULT_OFFLINE_DENIED);
        } else if (acType.type == ACType.AC_ARQC) {
            result.setTransResult(TransResultEnum.RESULT_REQ_ONLINE);
        } else if (acType.type == ACType.AC_TC) {
            result.setTransResult(TransResultEnum.RESULT_OFFLINE_APPROVED);
        } else {
            LogUtils.e(TAG, "acType Unkonwn = " + acType.type);
            result.setResultCode(RetCode.CLSS_PARAM_ERR);
            return result;
        }

        if (cvmType < 0) {
            LogUtils.e(TAG, "Clss_GetCvmType_AE ret = " + cvmType);
            if (cvmType == RetCode.CLSS_DECLINE) {
                result.setTransResult(TransResultEnum.RESULT_OFFLINE_DENIED);
                result.setResultCode(RetCode.CLSS_DECLINE);
            }
            return result;
        }

        if (cvmType == CvmType.RD_CVM_NO) {
            result.setCvmResult(CvmResultEnum.CVM_NO_CVM);
        } else if (cvmType == CvmType.RD_CVM_SIG) {
            result.setCvmResult(CvmResultEnum.CVM_SIG);
        } else if (cvmType == CvmType.RD_CVM_ONLINE_PIN) {
            result.setCvmResult(CvmResultEnum.CVM_ONLINE_PIN);
        } else {
            LogUtils.e(TAG, "cvmType Unkonwn = " + cvmType);
            result.setResultCode(RetCode.CLSS_PARAM_ERR);
            return result;
        }

        return result;
    }

    @Override
    public TransResult completeTransProcess(IssuerRspData issuerRspData) {
        int ret;
        ONLINE_PARAM onlineParam = new ONLINE_PARAM();
        onlineParam.aucRspCode = issuerRspData.getRespCode();
        onlineParam.aucAuthCode = issuerRspData.getAuthCode();
        onlineParam.aucIAuthData = issuerRspData.getAuthData();
        onlineParam.nAuthCodeLen = issuerRspData.getAuthCode() != null ? issuerRspData.getAuthCode().length : 0;
        onlineParam.nIAuthDataLen = issuerRspData.getAuthData() != null ? issuerRspData.getAuthData().length : 0;
        onlineParam.aucScript = issuerRspData.getScript();
        ret = ClssAmexApi.Clss_CompleteTrans_AE(issuerRspData.getOnlineResult(), (byte) transMode.mode,
                onlineParam, new ByteArray());
        if (ret != EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_ONLINE_CARD_DENIED, CvmResultEnum.CVM_NO_CVM);
        }
        return new TransResult(EMV_OK, TransResultEnum.RESULT_ONLINE_APPROVED, CvmResultEnum.CVM_NO_CVM);
    }

    @Override
    public int getTlv(int tag, ByteArray value) {
        int ret = ClssAmexApi.Clss_GetTLVData_AE((short) tag, value);
        EmvDebugger.d(TAG, "getTlv", tag, value);
        return ret;
    }

    @Override
    public int setTlv(int tag, byte[] value) {
        EmvDebugger.d(TAG, "setTlv", tag, value);
        return ClssAmexApi.Clss_SetTLVData_AE((short) tag, value, value.length);
    }

    @Override
    protected int addCapkAndRevokeList(EMV_CAPK emvCapk, EMV_REVOCLIST emvRevoclist) {
        int ret = EMV_OK;
        if (emvCapk != null) {
            ClssAmexApi.Clss_DelAllCAPK_AE();
            ret = ClssAmexApi.Clss_AddCAPK_AE(emvCapk);
            if (ret != EMV_OK) {
                LogUtils.e(TAG, "Clss_AddCAPK_AE ret = " + ret);
                return ret;
            }
        }
        if (emvRevoclist != null) {
            ClssAmexApi.Clss_DelAllRevocList_AE();
            return ClssAmexApi.Clss_AddRevocList_AE(emvRevoclist);
        }
        return ret;
    }

    @Override
    public String getTrack2() {
        ByteArray byteArray = new ByteArray();
        getTlv(0x57, byteArray);
        return getTrack2FromTag57(ConvertUtils.bcd2Str(byteArray.data, byteArray.length));
    }

    @Override
    public boolean isNeedSecondTap(IssuerRspData issuerRspData) {
        return false;
    }
}
