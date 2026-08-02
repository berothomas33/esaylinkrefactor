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

package com.pax.emvlib.dpas2.contactless;

import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.constant.TagsTable;
import com.pax.emvbase.param.clss.DpasParam;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvbase.utils.EmvDebugger;
import com.pax.emvlib.base.consts.EmvKernelConst;
import com.pax.emvlib.base.contactless.ClssKernelProcess;
import com.pax.jemv.clcommon.ByteArray;
import com.pax.jemv.clcommon.EMV_CAPK;
import com.pax.jemv.clcommon.EMV_REVOCLIST;
import com.pax.jemv.clcommon.OutcomeParam;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.clcommon.TransactionPath;
import com.pax.jemv.dpas.api.ClssDPASApi;
import com.sankuai.waimai.router.annotation.RouterService;
import java.util.Arrays;

/**
 * D-PAS connect 2.0 Contactless Process.
 */
@RouterService(interfaces = ClssKernelProcess.class, key = EmvKernelConst.DPAS)
public class ClssDpasProcess extends ClssKernelProcess<DpasParam> {
    private static final String TAG = "ClssDpasProcess";

    @Override
    public TransResult startTransProcess() {
        int ret = init();
        if (ret != RetCode.EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        setParam();

        ret = ClssDPASApi.Clss_InitiateApp_DPAS(transactionPath);
        if (ret != RetCode.EMV_OK) {
            LogUtils.e(TAG, "Clss_InitiateApp_DPAS ret = " + ret);
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        // Only support EMV mode
        LogUtils.d(TAG, "trans path: " + transactionPath.path);

        ret = processDpas(transactionPath.path);
        if (ret != RetCode.EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        if (clssStatusListener != null) {
            clssStatusListener.onReadCardOk();
            clssStatusListener.onRemoveCard();
        }

        return genTransResult();
    }

    private int init() {
        int ret = ClssDPASApi.Clss_CoreInit_DPAS();
        if (ret != RetCode.EMV_OK) {
            LogUtils.e(TAG, "Clss_CoreInit_DPAS ret = " + ret);
            return ret;
        }

        ret = ClssDPASApi.Clss_SetFinalSelectData_DPAS(finalSelectData, finalSelectDataLen);
        if (ret != RetCode.EMV_OK) {
            LogUtils.e(TAG, "Clss_SetFinalSelectData_DPAS ret = " + ret);
            return ret;
        }

        return ret;
    }

    private void setParam() {
        setTlv(TagsTable.APP_VER, clssParam.getAppVersion());
        setTlv(TagsTable.TAC_DEFAULT, clssParam.getTacDefault());
        setTlv(TagsTable.TAC_DENIAL, clssParam.getTacDenial());
        setTlv(TagsTable.TAC_ONLINE, clssParam.getTacOnline());

        setTlv(TagsTable.TERMINAL_CAPABILITY, clssParam.getTermCapability());
        setTlv(TagsTable.ACQUIRER_ID, clssParam.getAcquirerId());
        setTlv(TagsTable.MERCHANT_CATEGORY_CODE, clssParam.getMerchantCategoryCode());
        setTlv(TagsTable.MERCHANT_NAME_LOCATION, clssParam.getMerchantNameLocation());
        setTlv(TagsTable.COUNTRY_CODE, clssParam.getCountryCode());
        setTlv(TagsTable.TERMINAL_TYPE, clssParam.getTermTypeBytes());//TerminalType
        setTlv(TagsTable.CURRENCY_CODE, clssParam.getTransCurrCode());
        setTlv(TagsTable.TRANS_CURRENCY_EXPONENT, clssParam.getTransCurrExpBytes());//Transaction Currency Exponent
        // DO NOT use aid ttq value directly. preProcInterInfo.aucReaderTTQ may difference with
        // aid ttq value. If use aid ttq value directly, it may cause some test case execute failed.
        setTlv(TagsTable.TTQ, preProcInterInfo.aucReaderTTQ);

        if (preProcInterInfo.ucRdCLFLmtExceed == 1) {
            ByteArray TVR = new ByteArray();
            getTlv(TagsTable.TVR, TVR);
            TVR.data[3] = (byte) (TVR.data[3] | 0x80);
            setTlv(TagsTable.TVR, Arrays.copyOfRange(TVR.data, 0, 5));
        }

        setTlv(TagsTable.AMOUNT, clssParam.getAmount());
        setTlv(TagsTable.AMOUNT_OTHER, clssParam.getOtherAmount());

        setTlv(TagsTable.TRANS_TYPE, clssParam.getTransTypeBytes());
        setTlv(TagsTable.TRANS_DATE, clssParam.getTransDate());
        setTlv(TagsTable.TRANS_TIME, clssParam.getTransTime());
    }

    private int processDpas(int pathType) {
        int ret;
        ret = ClssDPASApi.Clss_ReadData_DPAS();
        if (ret != RetCode.EMV_OK) {
            LogUtils.e(TAG, "Clss_ReadData_DPAS ret = " + ret);
            return ret;
        }

        if (pathType == TransactionPath.CLSS_DPAS_EMV) {
            ClssDPASApi.Clss_DelAllRevocList_DPAS();
            ClssDPASApi.Clss_DelAllCAPK_DPAS();
            addCapkRevList();
        }

        ret = ClssDPASApi.Clss_TransProc_DPAS(clssParam.getExceptFileFlag());
        if (ret != RetCode.EMV_OK) {
            LogUtils.e(TAG, "Clss_TransProc_DPAS ret = " + ret);
        }
        return ret;
    }

    private TransResult genTransResult() {
        ByteArray outcomeParamSet = new ByteArray(8);
        ByteArray errIndication = new ByteArray(6);
        ByteArray userInterReqData = new ByteArray(22);
        getTlv(TagsTable.LIST, outcomeParamSet);
        getTlv(0xDF8115, errIndication);
        getTlv(0xDF8116, userInterReqData);

        TransResult result = new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);

        if (outcomeParamSet.data[0] == 0x70
                || outcomeParamSet.data[1] != (byte) 0xF0) {
            result.setTransResult(TransResultEnum.RESULT_TRY_AGAIN);
            return result;
        }

        switch (outcomeParamSet.data[0] & 0xF0) {
            case OutcomeParam.CLSS_OC_APPROVED:
                LogUtils.i(TAG, "genTransResult RESULT_OFFLINE_APPROVED");
                result.setTransResult(TransResultEnum.RESULT_OFFLINE_APPROVED);
                break;
            case OutcomeParam.CLSS_OC_ONLINE_REQUEST:
                LogUtils.i(TAG, "genTransResult RESULT_REQ_ONLINE");
                result.setTransResult(TransResultEnum.RESULT_REQ_ONLINE);
                break;
            case OutcomeParam.CLSS_OC_TRY_ANOTHER_INTERFACE:
                LogUtils.i(TAG, "genTransResult RESULT_CLSS_TRY_ANOTHER_INTERFACE");
                result.setTransResult(TransResultEnum.RESULT_CLSS_TRY_ANOTHER_INTERFACE);
                break;
            case OutcomeParam.CLSS_OC_TRY_AGAIN:
                LogUtils.i(TAG, "genTransResult RESULT_TRY_AGAIN");
                result.setTransResult(TransResultEnum.RESULT_TRY_AGAIN);
                break;
            case OutcomeParam.CLSS_OC_DECLINED:
            default:
                LogUtils.i(TAG, "default genTransResult CLSS_OC_DECLINED");
                result.setTransResult(TransResultEnum.RESULT_OFFLINE_DENIED);
                break;
        }

        switch (outcomeParamSet.data[3] & 0xF0) {
            case OutcomeParam.CLSS_OC_OBTAIN_SIGNATURE:
                result.setCvmResult(CvmResultEnum.CVM_SIG);
                LogUtils.i(TAG, "CVM = signature");
                break;
            case OutcomeParam.CLSS_OC_ONLINE_PIN:
                result.setCvmResult(CvmResultEnum.CVM_ONLINE_PIN);
                LogUtils.i(TAG, "CVM = online pin");
                break;
            case OutcomeParam.CLSS_OC_CONFIRM_CODE_VER:
                result.setCvmResult(CvmResultEnum.CVM_CONSUMER_DEVICE);
                LogUtils.i(TAG, "CVM = consumer device");
                break;
            case OutcomeParam.CLSS_OC_NO_CVM:
            default:
                result.setCvmResult(CvmResultEnum.CVM_NO_CVM);
                LogUtils.i(TAG, "CVM = no cvm");
                break;
        }

        return result;
    }

    @Override
    public TransResult completeTransProcess(IssuerRspData issuerRspData) {
        int ret = RetCode.EMV_OK;
        byte[] script = issuerRspData.getScript();
        if (script == null) {
            return new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_ONLINE_APPROVED, CvmResultEnum.CVM_NO_CVM);
        }

        ret = ClssDPASApi.Clss_IssuerUpdateProc_DPAS(issuerRspData.getOnlineResult(), script, script.length);
        LogUtils.i(TAG, "Clss_IssuerUpdateProc_DPAS online result = "
                + issuerRspData.getOnlineResult() + "ret =" + ret);
        if (ret != RetCode.EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_ONLINE_CARD_DENIED,
                    CvmResultEnum.CVM_NO_CVM);
        }

        return new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_ONLINE_APPROVED, CvmResultEnum.CVM_NO_CVM);
    }

    @Override
    public int getTlv(int tag, ByteArray value) {
        int ret;
        byte[] bcdTag = ConvertUtils.intToByteArray(tag, ConvertUtils.EEndian.BIG_ENDIAN);

        ret = ClssDPASApi.Clss_GetTLVDataList_DPAS(bcdTag, (byte) bcdTag.length, value.length, value);
        EmvDebugger.d(TAG, "getTlv", bcdTag, value);
        return ret;
    }

    @Override
    public int setTlv(int tag, byte[] value) {
        byte[] bcdTag = ConvertUtils.intToByteArray(tag, ConvertUtils.EEndian.BIG_ENDIAN);
        byte[] buf = new byte[bcdTag.length + 1 + (value != null ? value.length : 0)];

        System.arraycopy(bcdTag, 0, buf, 0, bcdTag.length);
        if (value != null) {
            buf[bcdTag.length] = (byte) value.length;
            System.arraycopy(value, 0, buf, bcdTag.length + 1, value.length);
        } else {
            buf[bcdTag.length] = 0x00;
        }

        int ret = ClssDPASApi.Clss_SetTLVDataList_DPAS(buf, buf.length);
        EmvDebugger.d(TAG, "setTlv", bcdTag, value);
        return ret;
    }

    @Override
    protected int addCapkAndRevokeList(EMV_CAPK emvCapk, EMV_REVOCLIST emvRevoclist) {
        int ret = ClssDPASApi.Clss_AddCAPK_DPAS(emvCapk);
        LogUtils.i(TAG, "Clss_AddCAPK_DPAS ret = " + ret);
        ret = ClssDPASApi.Clss_AddRevocList_DPAS(emvRevoclist);
        LogUtils.i(TAG, "Clss_AddRevocList_DPAS ret = " + ret);
        return ret;
    }

    @Override
    public String getTrack2() {
        ByteArray track = new ByteArray();
        if (getTlv(TagsTable.TRACK2, track) == RetCode.EMV_OK) {
            return getTrack2FromTag57(ConvertUtils.bcd2Str(track.data, track.length));
        }
        return "";
    }

    @Override
    public boolean isNeedSecondTap(IssuerRspData issuerRspData) {
        return issuerRspData != null
                && issuerRspData.getScript() != null
                && issuerRspData.getScript().length != 0
                && transactionPath.path == TransactionPath.CLSS_DPAS_EMV;
    }
}
