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
import com.pax.emvbase.constant.TagsTable;
import com.pax.emvbase.param.clss.PayPassParam;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvbase.utils.EmvDebugger;
import com.pax.emvlib.base.contactless.ClssKernelProcess;
import com.pax.jemv.clcommon.ACType;
import com.pax.jemv.clcommon.ByteArray;
import com.pax.jemv.clcommon.EMV_CAPK;
import com.pax.jemv.clcommon.EMV_REVOCLIST;
import com.pax.jemv.clcommon.OutcomeParam;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.clcommon.TransactionPath;
import com.pax.jemv.device.model.ApduRespL2;
import com.pax.jemv.device.model.ApduSendL2;
import com.pax.jemv.paypass.api.ClssPassApi;
import com.pax.jemv.paypass.listener.ClssPassCBFunApi;
import com.pax.jemv.paypass.listener.IClssPassCBFun;

public class ClssPayPassProcess extends ClssKernelProcess<PayPassParam> {
    private static final String TAG = "ClssPayPassProcess";
    private ClssPassListener clssPassListener = new ClssPassListener();
    private ClssPassCBFunApi passCBFun = ClssPassCBFunApi.getInstance();

    @Override
    protected int addCapkAndRevokeList(EMV_CAPK emvCapk, EMV_REVOCLIST emvRevoclist) {
        int ret = 0;

        if(emvCapk != null) {
            ret = ClssPassApi.Clss_AddCAPK_MC_MChip(emvCapk);
            if (ret != 0) {
                LogUtils.e(TAG, "Clss_AddCAPK_MC_MChip ret :" + ret);
                return ret;
            }
        }

        if(emvRevoclist != null) {
            ret = ClssPassApi.Clss_AddRevocList_MC_MChip(emvRevoclist);
            if (ret != 0) {
                LogUtils.e(TAG, "Clss_AddRevocList_MC_MChip ret :" + ret);
                return ret;
            }
        }

        return RetCode.EMV_OK;
    }

    private int coreInit(){
        int ret = ClssPassApi.Clss_CoreInit_MC(clssParam.getDataExchangeSupportFlag());
        if(ret != 0){
            LogUtils.e(TAG, "Clss_CoreInit_MC = " + ret);
            return ret;
        }

        //set the timer number for kernel [1/21/2014 ZhouJie]
        ClssPassApi.Clss_SetParam_MC(clssParam.getTlvParam(), clssParam.getTlvParam().length);

        passCBFun.setICBFun(clssPassListener);
        ClssPassApi.Clss_SetCBFun_SendTransDataOutput_MC();

        return ClssPassApi.Clss_SetFinalSelectData_MC(finalSelectData, finalSelectDataLen);
    }

    @Override
    public TransResult startTransProcess() {
        int ret = coreInit();
        if(ret != RetCode.EMV_OK){
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        setPayPassParam();

        ret = ClssPassApi.Clss_InitiateApp_MC();
        if(ret != RetCode.EMV_OK){
            LogUtils.e(TAG, "Clss_InitiateApp_MC ret :" + ret);
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        ret = ClssPassApi.Clss_ReadData_MC(transactionPath);
        if(ret != RetCode.EMV_OK){
            LogUtils.e(TAG, "Clss_ReadData_MC ret :" + ret);
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }


        ACType acType = new ACType();
        if (transactionPath.path == TransactionPath.CLSS_MC_MCHIP) {
            ret = processMChip(acType);
        } else if (transactionPath.path == TransactionPath.CLSS_MC_MAG) {
            ret = processMag(acType);
        }

        // send cmd err, can not prompt read card ok
        if(ret == RetCode.ICC_CMD_ERR){
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        if (clssStatusListener != null) {
            clssStatusListener.onReadCardOk();
            clssStatusListener.onRemoveCard();
        }
        /*
         * see phone
         * If ‘On device cardholder verification is supported' (TAG '82' Byte1 b2)in Application Interchange Profile is set and
         * 'On device cardholder verification supported'(TAG 'DF811B' Byte1,b6) in Kernel Configuration is set,
         * the kernel will return SEE PHONE in Message Identifier (byte1) of DF8116.
         */
        LogUtils.d(TAG, "clssPassListener.userInterReqData.data[0] = " + clssPassListener.userInterReqData.data[0]);
        if(clssPassListener.userInterReqData.data[0] == 0x20){
            return new TransResult(ret, TransResultEnum.RESULT_CLSS_SEE_PHONE, CvmResultEnum.CVM_CONSUMER_DEVICE);
        }

        if(ret != RetCode.EMV_OK){
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        if (enableDebugLog) {
            LogUtils.d(TAG, "outcomeParamSet data:" + ConvertUtils.bcd2Str(clssPassListener.outcomeParamSet.data));
        }
        if (clssPassListener.outcomeParamSet.data[0] == 0x70 || clssPassListener.outcomeParamSet.data[1] != (byte) 0xF0) {
            return new TransResult(ret, TransResultEnum.RESULT_TRY_AGAIN, CvmResultEnum.CVM_NO_CVM);
        }

        return genTransResult();
    }


    //generate transult and cvm result
    private TransResult genTransResult() {
        TransResult transResult = new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);

        //set TransResult
        LogUtils.i(TAG, ConvertUtils.bcd2Str(clssPassListener.outcomeParamSet.data));
        switch (clssPassListener.outcomeParamSet.data[0] & 0xF0) {
            case OutcomeParam.CLSS_OC_APPROVED:
                transResult.setTransResult(TransResultEnum.RESULT_OFFLINE_APPROVED);
                break;
            case OutcomeParam.CLSS_OC_ONLINE_REQUEST:
                transResult.setTransResult(TransResultEnum.RESULT_REQ_ONLINE);
                break;
            case OutcomeParam.CLSS_OC_TRY_ANOTHER_INTERFACE:
                transResult.setTransResult(TransResultEnum.RESULT_CLSS_TRY_ANOTHER_INTERFACE);
                transResult.setResultCode(RetCode.CLSS_USE_CONTACT);
                break;
            case OutcomeParam.CLSS_OC_DECLINED:
            default:
                transResult.setTransResult(TransResultEnum.RESULT_OFFLINE_DENIED);
        }

        //set cvm result
        switch (clssPassListener.outcomeParamSet.data[3] & 0xF0) {
            case OutcomeParam.CLSS_OC_OBTAIN_SIGNATURE:
                transResult.setCvmResult(CvmResultEnum.CVM_SIG);
                LogUtils.d(TAG, "CVM = signature");
                break;
            case OutcomeParam.CLSS_OC_ONLINE_PIN:
                transResult.setCvmResult(CvmResultEnum.CVM_ONLINE_PIN);
                LogUtils.d(TAG, "CVM = online pin");
                break;
            case OutcomeParam.CLSS_OC_CONFIRM_CODE_VER:
                // Now the contactless kernel does not support Offline PIN temporarily, so this CVM
                // mode is equivalent to No CVM.
                transResult.setCvmResult(CvmResultEnum.CVM_OFFLINE_PIN);
                LogUtils.d(TAG, "CVM = offline pin");
                break;
            case OutcomeParam.CLSS_OC_NO_CVM:
            default:
                transResult.setCvmResult(CvmResultEnum.CVM_NO_CVM);
                LogUtils.d(TAG, "CVM = no cvm");
                break;
        }

        return transResult;
    }

    private int processMChip(ACType acType) {
        ClssPassApi.Clss_DelAllRevocList_MC_MChip();
        ClssPassApi.Clss_DelAllCAPK_MC_MChip();
        addCapkRevList();


        int ret = ClssPassApi.Clss_TransProc_MC_MChip(acType);
        LogUtils.d(TAG, "Clss_TransProc_MC_MChip = " + ret + "  ACType = " + acType.type);
        if (enableDebugLog) {
            LogUtils.d(TAG, "debug info: " + ClssPassApi.Clss_GetDebugInfo_MC());
        }

        return ret;
    }

    private int processMag(ACType acType) {
        int ret = ClssPassApi.Clss_TransProc_MC_Mag(acType);
        LogUtils.d(TAG, "Clss_TransProc_MC_Mag = " + ret + "  ACType = " + acType.type);
        return ret;
    }

    /**
     * PayPass does not have sencond Gac, do nothing in this process
     */
    @Override
    public TransResult completeTransProcess(IssuerRspData issuerRspData) {
        return new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_ONLINE_APPROVED, CvmResultEnum.CVM_NO_CVM);
    }

    @Override
    public int getTlv(int tag, ByteArray value) {
        byte[] bcdTag = ConvertUtils.intToByteArray(tag, ConvertUtils.EEndian.BIG_ENDIAN);//convert hex to bcd, 0x1234->byte[]{0x12,0x34}
        int ret = ClssPassApi.Clss_GetTLVDataList_MC(bcdTag, (byte) bcdTag.length, value.length, value);
        EmvDebugger.d(TAG, "getTlv", bcdTag, value);
        return ret;
    }

    @Override
    public int setTlv(int tag, byte[] value) {
        byte[] bcdTag = ConvertUtils.intToByteArray(tag, ConvertUtils.EEndian.BIG_ENDIAN);//convert hex to bcd, 0x1234->byte[]{0x12,0x34}
        byte[] buf = new byte[bcdTag.length + 1 + (value != null ? value.length : 0)];

        System.arraycopy(bcdTag, 0, buf, 0, bcdTag.length);
        if (value != null) {
            buf[bcdTag.length] = (byte) value.length;
            System.arraycopy(value, 0, buf, bcdTag.length + 1, value.length);
        } else {
            buf[bcdTag.length] = 0x00;
        }
        EmvDebugger.d(TAG, "setTlv", bcdTag, value);
        return ClssPassApi.Clss_SetTLVDataList_MC(buf, buf.length);
    }

    @Override
    public String getTrack2() {
        ByteArray track = new ByteArray();
        int ret = -1;
        if (transactionPath.path == TransactionPath.CLSS_MC_MCHIP) {
            ret = getTlv(0x57, track);
        } else if (transactionPath.path == TransactionPath.CLSS_MC_MAG) {
            ret = getTlv(0X9F6B, track);
        }

        if (ret == RetCode.EMV_OK) {
            return getTrack2FromTag57(ConvertUtils.bcd2Str(track.data, track.length));
        }
        LogUtils.e(TAG, "paypass getTrack2 error ret :" + ret);
        return "";
    }

    //paypass no second tap
    @Override
    public boolean isNeedSecondTap(IssuerRspData issuerRspData) {
        LogUtils.d(TAG, "paywave check if need second tap");
        return false;
    }

    private int setAidParam() {

        byte transType = clssParam.getTransType();
        //refund or void, need to req AAC
        //Refund required AAC  collis case:1.205 Test: PPC.MCD.03.Test.06.Scenario.01
        if(transType == 0x20 || transType == 0x02){
            setTlv(TagsTable.FLOOR_LIMIT, clssParam.getRefundVoidFloorLimit());//Reader Contactless Floor Limit
            setTlv(TagsTable.TAC_DENIAL, clssParam.getRefundVoidTacDenial());//Terminal Action Code – Denial
        }else{
            //Reader Contactless Floor Limit
            setTlv(TagsTable.FLOOR_LIMIT, clssParam.getFloorLimitBytes());
            setTlv(TagsTable.TAC_DENIAL, clssParam.getTacDenial());//Terminal Action Code – Denial
        }

        //Reader Contactless Transaction Limit (No On-device CVM)
        setTlv(TagsTable.TRANS_LIMIT, clssParam.getTransLimitBytes());

        //Reader Contactless Transaction Limit (On-device CVM)
        setTlv(TagsTable.TRANS_CVM_LIMIT, clssParam.getCvmTransLimit());

        //Reader CVM Required Limit
        setTlv(TagsTable.CVM_LIMIT, clssParam.getCvmLimitBytes());

        setTlv(TagsTable.TAC_DEFAULT, clssParam.getTacDefault());//Terminal Action Code – Default
        setTlv(TagsTable.TAC_ONLINE, clssParam.getTacOnline());//Terminal Action Code – Online
        setTlv(TagsTable.KERNEL_CFG, clssParam.getKernelConfig());//Kernel Configuration
        setTlv(0x9F1D, clssParam.getTermRiskManage());//Terminal Risk Management Data
        setTlv(TagsTable.CARD_DATA, clssParam.getCardDataInput());//Card Data Input Capability
        setTlv(TagsTable.CVM_REQ, clssParam.getCvmRequired());//CVM Capability – CVM Required
        setTlv(TagsTable.CVM_NO, clssParam.getNoCvmRequired());//CVM Capability – No CVM Required
        setTlv(TagsTable.SEC, clssParam.getSecurityCapability());//Security Capability
        setTlv(TagsTable.MAG_CVM_REQ, clssParam.getMagCvm());//Mag-stripe CVM Capability – CVM Required
        setTlv(TagsTable.MAG_CVM_NO, clssParam.getMagNoCvm());//Mag-stripe CVM Capability – No CVM Required

        setTlv(0xDF811C, clssParam.getMaxTornLifetime());//Max Lifetime of Torn Transaction Log Record
        setTlv(TagsTable.MAX_TORN, clssParam.getMaxTornNum());//Max Number of Torn Transaction Log Records

        setTlv(0xDF810C, clssParam.getKernelId());//Kernel ID
        setTlv(TagsTable.TERMINAL_TYPE, clssParam.getTermTypeBytes());//Terminal Type
        setTlv(TagsTable.ADDITIONAL_CAPABILITY, clssParam.getAddCapability());//Additional Terminal Capabilities
        setTlv(TagsTable.APP_VER, clssParam.getAppVersion());//Application Version Number
        setTlv(TagsTable.DEF_UDOL, clssParam.getDefaultUDOL());//Default UDOL

        return RetCode.EMV_OK;
    }

    private void setTermConfig(){
        setTlv(TagsTable.MERCHANT_ID, clssParam.getMerchantId());//Merchant Identifier
        setTlv(TagsTable.MERCHANT_CATEGORY_CODE, clssParam.getMerchantCategoryCode());//Merchant Category Code
        setTlv(TagsTable.MERCHANT_NAME_LOCATION, clssParam.getMerchantNameLocation());//Merchant Name and Location
        setTlv(TagsTable.COUNTRY_CODE, clssParam.getCountryCode());//Terminal Country Code
        setTlv(0x9F3C, clssParam.getReferCurrCode());//Transaction Reference Currency Code
        setTlv(0x9F3D, clssParam.getReferCurrCode());//Transaction Reference Currency Exponent
    }

    private void setTransParam(){
        setTlv(TagsTable.AMOUNT, clssParam.getAmount());
        setTlv(TagsTable.AMOUNT_OTHER, clssParam.getOtherAmount());

        setTlv(TagsTable.TRANS_TYPE, clssParam.getTransTypeBytes());
        setTlv(TagsTable.TRANS_DATE, clssParam.getTransDate());
        setTlv(TagsTable.TRANS_TIME, clssParam.getTransTime());
    }

    private void setPayPassParam(){
        if (clssParam.isSupportDefaultMcTermParam()) {
            setDefaultMcTermParam();// depend on the function you need.
        }
        setTransParam();
        int ret = setAidParam();
        if(ret != 0){
            return;
        }
        setTermConfig();
    }

    /**enable or disable kernel functions by Clss_SetTLVDataList_MC and Clss_SetTagPresent_MC
    * the detail describe in EMV Contactless Book C-2*/
    private void setDefaultMcTermParam() {
        setTlv(TagsTable.ACCOUNT_TYPE, null);
        setTlv(TagsTable.ACQUIRER_ID, null);
        setTlv(TagsTable.INTER_DEV_NUM, clssParam.getDeviceSN());
        setTlv(TagsTable.MOB_SUP, null);

        setTlv(TagsTable.DS_AC_TYPE, null);
        setTlv(TagsTable.DS_INPUT_CARD, null);
        setTlv(TagsTable.DS_INPUT_TERMINAL, null);
        setTlv(TagsTable.DS_ODS_INFO, null);
        setTlv(TagsTable.DS_ODS_READER, null);
        setTlv(TagsTable.DS_ODS_TERMINAL, null);

        setTagPresent(TagsTable.BALANCE_BEFORE_GAC, false);
        setTagPresent(TagsTable.BALANCE_AFTER_GAC, false);
        setTagPresent(TagsTable.MESS_HOLD_TIME, false);

        setEmptyTlv(TagsTable.FST_WRITE);
        setEmptyTlv(TagsTable.READ);
        setEmptyTlv(TagsTable.WIRTE_BEFORE_AC);
        setEmptyTlv(TagsTable.WIRTE_AFTER_AC);
        setEmptyTlv(TagsTable.TIMEOUT);

        setTlv(TagsTable.DS_OPERATOR_ID, clssParam.getDsOperatorId());
        setTagPresent(0xDF810D, false);
        setTagPresent(0x9F70, false);
        setTagPresent(0x9F75, false);

        setTlv(0x9F6D, clssParam.getMagVersion());

        setTagPresent(0xDF8130, false);
        setTagPresent(TagsTable.MESS_HOLD_TIME, false);
    }

    private void setTagPresent(int tag, boolean present) {
        ClssPassApi.Clss_SetTagPresent_MC(
                ConvertUtils.intToByteArray(tag, ConvertUtils.EEndian.BIG_ENDIAN),
                (byte) (present ? 1 : 0)
        );
    }

    private void setEmptyTlv(int tag) {
        ClssPassApi.Clss_SetTLVDataList_MC(
                ConvertUtils.intToByteArray(tag, ConvertUtils.EEndian.BIG_ENDIAN),
                0
        );
    }


    private class ClssPassListener implements IClssPassCBFun {
        ByteArray outcomeParamSet = new ByteArray(8);
        ByteArray userInterReqData = new ByteArray(22);
        ByteArray errIndication = new ByteArray(6);

        @Override
        public int sendDEKData(byte[] bytes, int i) {
            return 0;
        }

        @Override
        public int receiveDETData(ByteArray byteArray, byte[] bytes) {
            return 0;
        }

        @Override
        public int addAPDUToTransLog(ApduSendL2 apduSendL2, ApduRespL2 apduRespL2) {
            return 0;
        }

        @Override
        public int sendTransDataOutput(byte b) {
            if ((b & 0x01) != 0) {
                getTlv(0xDF8129, outcomeParamSet);//Outcome Parameter Set
            }

            if ((b & 0x04) != 0) {
                getTlv(0xDF8116, userInterReqData);//User Interface Request Data
            }

            if ((b & 0x02) != 0) {
                getTlv(0xDF8115, errIndication);//Error Indication
            }
            return RetCode.EMV_OK;
        }
    }
}
