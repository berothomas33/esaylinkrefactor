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

package com.pax.emvlib.dpas2.contact;

import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.BuildConfig;
import com.pax.emvbase.constant.TagsTable;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.param.common.Capk;
import com.pax.emvbase.param.common.CapkRevoke;
import com.pax.emvbase.param.common.Config;
import com.pax.emvbase.param.contact.EmvAid;
import com.pax.emvbase.process.contact.CandidateAID;
import com.pax.emvbase.process.contact.IContactCallback;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvbase.utils.EmvDebugger;
import com.pax.emvlib.base.consts.EmvKernelConst;
import com.pax.emvlib.base.contact.BaseContactProcess;
import com.pax.emvlib.base.utils.EmvParamConvert;
import com.pax.jemv.clcommon.ACType;
import com.pax.jemv.clcommon.ByteArray;
import com.pax.jemv.clcommon.EMV_APPLIST;
import com.pax.jemv.clcommon.EMV_CAPK;
import com.pax.jemv.clcommon.EMV_REVOCLIST;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.device.DeviceManager;
import com.pax.jemv.emv.api.EMVCallback;
import com.pax.jemv.emv.model.EmvMCKParam;
import com.pax.jemv.emv.model.EmvParam;
import com.sankuai.waimai.router.annotation.RouterService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Contact Process. Use D-PAS connect 2.0.
 */
@RouterService(interfaces = BaseContactProcess.class, key = EmvKernelConst.EMV)
public class ContactProcess extends BaseContactProcess {
    private static final String TAG = "EmvProcess";
    private static final boolean enableDebugLog = BuildConfig.DEBUG;

    private EmvParam emvParam;
    private EmvMCKParam mckParam;
    private EMVCallback emvCallback;
    private IContactCallback emvProcessListener;

    private ArrayList<Capk> capkParamList;
    private ArrayList<CapkRevoke> revokeList;
    private List<EmvAid> aidList;
    private EmvTransParam transParam;
    private Config terminalCfg;

    private boolean isSelectedAIDSupportPINByPass;

    @Override
    public void registerEmvProcessListener(IContactCallback emvTransProcessListener) {
        this.emvProcessListener = emvTransProcessListener;
    }

    @Override
    public int preTransProcess(EmvProcessParam emvProcessParam) {
        capkParamList = emvProcessParam.getCapkParam().getCapkList();
        revokeList = emvProcessParam.getCapkParam().getCapkRevokeList();
        transParam = emvProcessParam.getEmvTransParam();
        aidList = emvProcessParam.getEmvAidList();
        terminalCfg = emvProcessParam.getTermConfig();
        LogUtils.w(TAG, "capkList:" + capkParamList.size());
        LogUtils.w(TAG, "revokeList:" + revokeList.size());
        //1.Core init
        int ret = EMVCallback.EMVCoreInit();
        if (ret != RetCode.EMV_OK) {
            return ret;
        }
        ret = EMVCallback.DPASCTCoreInit();
        if (ret != RetCode.EMV_OK) {
            LogUtils.e(TAG, "DPASCTCoreInit: " + ret);
            return ret;
        }
        //2.set param(trans amt,ICS)
        ret = setEmvAndMCKParam();
        if (ret != RetCode.EMV_OK) {
            return ret;
        }
        //Set whether the kernel uses PCI verify offline PIN interface or not.
        // 1: use PIC verify offline PIN
        // 0: not use.
        EMVCallback.EMVSetPCIModeParam(transParam.getPciMode(), transParam.getPinLenSet(), transParam.getPciTimeout());
        //3.Add AID
        ret = addAID(aidList);
        return ret;
    }


    private int setEmvAndMCKParam() {
        emvParam = new EmvParam();
        mckParam = new EmvMCKParam();
        EMVCallback.EMVSetCallback();
        EMVCallback.EMVGetParameter(emvParam);
        EMVCallback.EMVGetMCKParam(mckParam);
        emvCallback = EMVCallback.getInstance();
        emvCallback.setCallbackListener(new EmvCallBackListener());

        if (aidList.isEmpty() || terminalCfg == null || transParam == null) {
            throw new IllegalArgumentException();
        }

        EmvAid emvAid = aidList.get(0);//first one is default
        emvParam.capability = emvAid.getTerminalCapability();
        emvParam.countryCode = terminalCfg.getTerminalCountryCode();
        emvParam.exCapability = emvAid.getAdditionalTerminalCapabilities();
        emvParam.forceOnline = (byte) emvAid.getForcedOnlineCapability();
        emvParam.getDataPIN = (byte) emvAid.getGetDataForPINTryCounter();
        emvParam.merchCateCode = terminalCfg.getMerchantCategoryCode();

        emvParam.referCurrCode = terminalCfg.getTransReferenceCurrencyCode();
        emvParam.referCurrCon = terminalCfg.getConversionRatio();
        emvParam.referCurrExp = terminalCfg.getTransReferenceCurrencyExponent();
        emvParam.surportPSESel = 0x01;
        emvParam.terminalType = emvAid.getTerminalType();
        emvParam.transCurrCode = transParam.getTransCurrencyCode();
        emvParam.transCurrExp = transParam.getTransCurrencyExponent();
        emvParam.transType = transParam.getTransType();
        emvParam.termId = transParam.getTerminalID();
        emvParam.merchId = terminalCfg.getMerchantId();
        emvParam.merchName = terminalCfg.getMerchantNameAndLocationBytes();

        mckParam.ucBypassPin = (emvAid.getBypassPINEntry() == 0) ? (byte) 0 : 1;
        mckParam.ucBatchCapture = 1;

        mckParam.extmParam.aucTermAIP = new byte[]{0x08, 0x00};// The bit4 of byte1 decide whether force to perform TRM: "08 00"-Yes;"00 00"-No(default)
        mckParam.extmParam.ucBypassAllFlg = (emvAid.getSubsequentBypassPINEntry() == 0) ? (byte) 0 : 1;
        mckParam.extmParam.ucUseTermAIPFlg = 1;// 0-TRM is based on AIP of card(default),1-TRM is based on AIP of Terminal

        EMVCallback.EMVSetParameter(emvParam);
        return EMVCallback.EMVSetMCKParam(mckParam);
    }

    private int addAID(List<EmvAid> aidList) {
        int ret = RetCode.EMV_OK;
        if (aidList == null) {
            throw new IllegalArgumentException();
        }
        LogUtils.w(TAG, "addAID aidList:" + aidList.size());

        for (EmvAid emvAid : aidList) {
            ret = EMVCallback.EMVAddApp(EmvParamConvert.toEMVApp(emvAid));
            if (ret != RetCode.EMV_OK) {
                if (enableDebugLog) {
                    LogUtils.e(TAG, "addAID error: ret = " + ret
                            + ", aid = " + ConvertUtils.bcd2Str(emvAid.getApplicationID()));
                }
                return ret;
            }
        }
        return ret;
    }

    @Override
    public TransResult startTransProcess() {
        LogUtils.i(TAG, "startTransProcess");
        //After detected card,start emv process
        //1.App select
        int ret = EMVCallback.EMVAppSelect(0, transParam.getTransTraceNo());
        LogUtils.i(TAG, "startTransProcess, EMVAppSelect ret:" + ret);
        if (ret != RetCode.EMV_OK) {
            switch (ret) {
                case RetCode.EMV_DATA_ERR:
                case RetCode.EMV_NO_APP:
                case RetCode.EMV_RSP_ERR:
                case RetCode.ICC_RSP_6985:
                case RetCode.ICC_RESET_ERR:
                case RetCode.ICC_CMD_ERR:
                    return new TransResult(ret, TransResultEnum.RESULT_FALLBACK, CvmResultEnum.CVM_NO_CVM);
                default:
                    return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
            }
        }
        //2.Read App Data
        ret = EMVCallback.EMVReadAppData();
        LogUtils.i(TAG, "startTransProcess, EMVReadAppData ret:" + ret);
        if (ret != RetCode.EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }
        if (emvProcessListener != null){
            ret = emvProcessListener.showConfirmCard();
            if (ret != RetCode.EMV_OK){//for example,timeout/data_error
                return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
            }
        }

        changeTAG9CValue();//PAX emv kernel not define the refund(0x20) trans,and the value will be transfer to 0x40

        //3.Add Capk revoke and Card Auth
        ret = addCapk();  //ignore return value for some case which the card doesn't has the capk index
        LogUtils.i(TAG, "startTransProcess, addCapk ret:" + ret);
        ret = EMVCallback.EMVCardAuth();
        LogUtils.i(TAG, "startTransProcess, EMVCardAuth ret:" + ret);
        if (ret != RetCode.EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        if (transParam.getFlowType() == EmvTransParam.FLOWTYPE_SIMPLE){
            return new TransResult(ret, TransResultEnum.RESULT_SIMPLE_FLOW_END, CvmResultEnum.CVM_NO_CVM);
        }

        //4. Processing restrictions->CardHolder verification->Terminal risk management->First GAC
        ACType acType = new ACType();
        long authAmt = transParam.getAmount();
        long cashbackAmt = transParam.getAmountOther();
        // If the transaction amount is greater than 4294967295 (0xFFFF FFFF), then you need to
        // use the following method to set the amount. At the same time, in the emvInputAmount()
        // method of EMVCallback.EmvCallbackListener, all the amounts in the array must be set to 0
        if (authAmt > 0xFFFFFFFF) {
            EMVCallback.EMVSetAmount(transParam.getAmountBytes(), transParam.getAmountOtherBytes());
        }
        ret = EMVCallback.EMVStartTrans(authAmt, cashbackAmt, acType);
        LogUtils.i(TAG, "startTransProcess, EMVStartTrans ret:" + ret + ", acType:" + acType.type);
        if (ret != RetCode.EMV_OK) {
            return new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        }

        TransResult transResult = new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
        transResult.setResultCode(ret);
        if (acType.type == ACType.AC_TC) {
            transResult.setTransResult(TransResultEnum.RESULT_OFFLINE_APPROVED);
        } else if (acType.type == ACType.AC_AAC) {
            transResult.setTransResult(TransResultEnum.RESULT_OFFLINE_DENIED);
        } else {
            transResult.setTransResult(TransResultEnum.RESULT_REQ_ONLINE);
        }
        transResult.setCvmResult(getCvm());
        return transResult;

    }

    private void changeTAG9CValue() {
        setTlv(0x9C, new byte[]{emvParam.transType});
    }

    private CvmResultEnum getCvm() {
        CvmResultEnum cvmResut = CvmResultEnum.CVM_ONLINE_PIN;

        byte[] cvmType = getTlv(0x9F34);
        if (cvmType.length > 0) {
            if (enableDebugLog) {
                LogUtils.i(TAG, "saveProcessData  cvmType:" + ConvertUtils.bcd2Str(cvmType));
            }
            switch (cvmType[0] & 0x3F) {//0011 1111
                case 0x01:
                case 0x04:
                    cvmResut = CvmResultEnum.CVM_OFFLINE_PIN;
                    break;
                case 0x1E:
                    cvmResut = CvmResultEnum.CVM_SIG;
                    break;
                case 0x03:
                case 0x05:
                    cvmResut = CvmResultEnum.CVM_ONLINE_PIN_SIG;
                    break;
                case 0x1F:
                    cvmResut = CvmResultEnum.CVM_NO_CVM;
                    break;
                case 0x02:
                default:
                    cvmResut = CvmResultEnum.CVM_ONLINE_PIN;
                    break;
            }
        }
        LogUtils.i(TAG, "getCvm result:" + cvmResut);
        return cvmResut;
    }

    private int addCapk() {
        //get Capk with matched RID and KeyID
        ByteArray dataList = new ByteArray();
        int ret = EMVCallback.EMVGetTLVData((short) TagsTable.CAPK_RID, dataList);
        if (ret != RetCode.EMV_OK) {
            ret = EMVCallback.EMVGetTLVData((short) 0x84, dataList);
        }
        if (dataList.length < 5) {
            return ret;
        }
        if (enableDebugLog) {
            LogUtils.d(TAG, "addCapk, dataList 1 :" + ConvertUtils.bcd2Str(dataList.data));
        }
        byte[] rid = new byte[5];
        System.arraycopy(dataList.data, 0, rid, 0, 5);
        if (enableDebugLog) {
            LogUtils.d(TAG, "addCapk, RID :" + ConvertUtils.bcd2Str(rid));
        }
        ret = EMVCallback.EMVGetTLVData((short) TagsTable.CAPK_ID, dataList);
        if (ret != RetCode.EMV_OK) {
            return ret;
        }
        if (enableDebugLog) {
            LogUtils.d(TAG, "addCapk, dataList 2 :" + ConvertUtils.bcd2Str(dataList.data));
        }
        byte keyId = dataList.data[0];
        LogUtils.d(TAG, "addCapk  KeyID :" + keyId);
        for (Capk capk : capkParamList) {
            if (new String(capk.getRid()).equals(new String(rid)) && capk.getKeyId() == keyId) {
                EMV_CAPK emvCapk = EmvParamConvert.toEMVCapk(capk);
                ret = EMVCallback.EMVAddCAPK(emvCapk);
            }
        }

        EMVCallback.EMVDelAllRevocList();
        for (CapkRevoke capkRevoke : revokeList) {
            if (new String(capkRevoke.getRid()).equals(new String(rid)) && capkRevoke.getKeyId() == keyId) {
                EMV_REVOCLIST emvRevocList = new EMV_REVOCLIST(rid, keyId, capkRevoke.getCertificateSN());
                ret = EMVCallback.EMVAddRevocList(emvRevocList);
            }
        }
        return ret;
    }

    @Override
    public TransResult completeTransProcess(IssuerRspData issuerRspData) {
        TransResult completeTransResult = new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_ONLINE_CARD_DENIED, CvmResultEnum.CVM_NO_CVM);
        ACType acType = new ACType();
        if (issuerRspData.getAuthCode().length != 0) {
            LogUtils.i(TAG, "");
            setTlv(0x89, issuerRspData.getAuthCode());
            if (enableDebugLog) {
                LogUtils.i(TAG, "auth code(89):" + ConvertUtils.bcd2Str(issuerRspData.getAuthCode()));
            }
        }
        if (issuerRspData.getAuthData().length != 0) {
            setTlv(0x91, issuerRspData.getAuthData());
            if (enableDebugLog) {
                LogUtils.i(TAG, "auth data(91):" + ConvertUtils.bcd2Str(issuerRspData.getAuthData()));
            }
        }
        if (enableDebugLog) {
            LogUtils.i(TAG, "online result:" + issuerRspData.getOnlineResult());
            LogUtils.i(TAG, "issuer script:" + ConvertUtils.bcd2Str(issuerRspData.getScript()));
        }
        int ret = EMVCallback.EMVCompleteTrans(issuerRspData.getOnlineResult(), issuerRspData.getScript(), issuerRspData.getScript().length, acType);
        LogUtils.d(TAG, "EMVCallback.EMVCompleteTrans ret =" + ret);

        if (ret != RetCode.EMV_OK) {
            if (enableDebugLog) {
                ByteArray scriptResult = new ByteArray();
                int ret2 = EMVCallback.EMVGetScriptResult(scriptResult);
                LogUtils.e(TAG, "EMVGetScriptResult scriptResult ret: " + ret2
                        + "result: " + ConvertUtils.bcd2Str(scriptResult.data));
            }
            completeTransResult.setResultCode(ret);
            completeTransResult.setTransResult(TransResultEnum.RESULT_ONLINE_CARD_DENIED);
            return completeTransResult;
        }
        completeTransResult.setResultCode(ret);
        LogUtils.i(TAG, "completeTransProcess,acType:" + acType.type);
        if (acType.type == ACType.AC_TC) {
            completeTransResult.setTransResult(TransResultEnum.RESULT_ONLINE_APPROVED);
        } else if (acType.type == ACType.AC_AAC) {
            completeTransResult.setTransResult(TransResultEnum.RESULT_ONLINE_CARD_DENIED);
        }
        return completeTransResult;
    }

    @Override
    public byte[] getTlv(int tag) {
        ByteArray value = new ByteArray();
        int ret = EMVCallback.EMVGetTLVData((short) tag, value);

        EmvDebugger.d(TAG, "getTlv", tag, value);
        if (ret != RetCode.EMV_OK) {
            return new byte[0];
        }
        return Arrays.copyOfRange(value.data, 0, value.length);
    }

    /**
     * Sets value on specific tag
     *
     * @param tag   emv tag
     * @param value tag value
     */
    @Override
    public void setTlv(int tag, byte[] value) {
        EMVCallback.EMVSetTLVData((short) tag, value, value.length);

        EmvDebugger.d(TAG, "setTlv", tag, value);
    }

    private void resetParam(byte[] aid) {
        EmvAid selectedAid = null;
        for (EmvAid emvAid : aidList) {
            //finalSelectData[0] is aid len
            if (Arrays.equals(aid, emvAid.getApplicationID()) //full match
                    || (emvAid.getPartialAIDSelection() == 0 && (emvAid.getApplicationID().length < aid.length) && (Arrays.equals(Arrays.copyOfRange(aid, 0, emvAid.getApplicationID().length), emvAid.getApplicationID())))) {//partial match
                selectedAid = emvAid;
            }
        }
        if (selectedAid == null) {
            return;
        }

        EMVCallback.EMVGetParameter(emvParam);
        emvParam.capability = selectedAid.getTerminalCapability();
        emvParam.exCapability = selectedAid.getAdditionalTerminalCapabilities();
        emvParam.forceOnline = (byte) selectedAid.getForcedOnlineCapability();
        emvParam.getDataPIN = (byte) selectedAid.getGetDataForPINTryCounter();
        emvParam.terminalType = selectedAid.getTerminalType();

        if (enableDebugLog) {
            LogUtils.d(TAG, "resetParam emvParam.capability =" + ConvertUtils.bcd2Str(emvParam.capability));
            LogUtils.d(TAG, "resetParam emvParam.exCapability =" + ConvertUtils.bcd2Str(emvParam.exCapability));
        }
        EMVCallback.EMVSetParameter(emvParam);

        // it seems that  after calling EMVSetParameter, the value of TAG 9F33 still didn't change
        // try to set the TAGs directly here
        EMVCallback.EMVSetTLVData((short) TagsTable.TERMINAL_CAPABILITY, emvParam.capability,
                emvParam.capability.length);
        EMVCallback.EMVSetTLVData((short) TagsTable.ADDITIONAL_CAPABILITY, emvParam.exCapability,
                emvParam.exCapability.length);
        EMVCallback.EMVSetTLVData((short) TagsTable.TERMINAL_TYPE, new byte[]{emvParam.terminalType}, 1);

        isSelectedAIDSupportPINByPass = (selectedAid.getBypassPINEntry() == 1);
        mckParam.ucBypassPin = (selectedAid.getBypassPINEntry() == 0) ? (byte) 0 : 1;
        mckParam.ucBatchCapture = 1;

        mckParam.extmParam.aucTermAIP = new byte[]{0x08, 0x00};
        mckParam.extmParam.ucBypassAllFlg = (selectedAid.getSubsequentBypassPINEntry() == 0) ? (byte) 0 : 1;
        mckParam.extmParam.ucUseTermAIPFlg = 1;

        int ret = EMVCallback.EMVGetMCKParam(mckParam);
        LogUtils.d(TAG, "resetParam EMVGetMCKParam ret=" + ret);


    }

    private class EmvCallBackListener implements EMVCallback.EmvCallbackListener {
        private boolean isFirstCall = true;
        private boolean isFirstOnline = true;
        private int tmpRemainCount = 0;

        @Override
        public void emvWaitAppSel(int tryCnt, EMV_APPLIST[] appLists, int appNum) {
            ArrayList<CandidateAID> candidateAIDS = new ArrayList<>();
            LogUtils.i(TAG, "candidate AID list size:" + appLists.length + ",appNum:" + appNum + ", tryCnt:" + tryCnt);
            CandidateAID candidateAID;
            int size = Math.min(appLists.length, appNum);
            for (int i = 0; i < size; i++) {
                candidateAID = new CandidateAID();
                candidateAID.setAid(appLists[i].aid);
                candidateAID.setAidLen(appLists[i].aidLen);
                candidateAID.setAppName(appLists[i].appName);
                candidateAID.setPriority(appLists[i].priority);
                candidateAIDS.add(candidateAID);
            }
            if (emvProcessListener != null) {
                int index = emvProcessListener.onWaitAppSelect(tryCnt <= 0, candidateAIDS);
                emvCallback.setCallBackResult(index);
            }
        }

        @Override
        public void emvInputAmount(long[] amt) {
            LogUtils.i(TAG, "emvInputAmount");
            long amount = transParam.getAmount();
            // If the transaction amount is greater than 4294967295 (0xFFFF FFFF), all the
            // amounts in the array must be set to 0
            if (amount > 0xFFFFFFFF) {
                amt[0] = 0;
                if (amt.length > 1) {
                    amt[1] = 0;
                }
            } else {
                amt[0] = amount;
                if (amt.length > 1) {
                    amt[1] = transParam.getAmountOther();
                }
            }

            emvCallback.setCallBackResult(RetCode.EMV_OK);
        }

        @Override
        public void emvGetHolderPwd(int tryFlag, int remainCnt, byte[] pinData) {
            boolean isOnline = (null == pinData);
            if (isOnline) {
                LogUtils.i(TAG, "emvGetHolderPwd pin is null, tryFlag" + tryFlag + " remainCnt:" + remainCnt);
            } else {
                LogUtils.i(TAG, "emvGetHolderPwd pin is not null, tryFlag" + tryFlag + " remainCnt:" + remainCnt);
            }
            if (this.isFirstCall) {
                this.tmpRemainCount = remainCnt;
                this.isFirstCall = false;
                this.isFirstOnline = isOnline;
            }
            if (isFirstOnline != isOnline) {
                // Please note that the CallBackResult set here will not directly terminate the
                // transaction. Whether the transaction should continue or not is determined by
                // the L2 library.
                // Another thing to note is that if you don't use setCallBackResult, it may cause
                // the transaction to get stuck. So make sure to call setCallBackResult() before
                // the callback execution is complete.
                emvCallback.setCallBackResult(RetCode.EMV_DATA_ERR);
                return;
            }

            if (pinData != null && pinData[0] != 0) {
                if (pinData[0] == 1) {
                    LogUtils.e(TAG, "enter pin timeout");
                    emvCallback.setCallBackResult(RetCode.EMV_TIME_OUT);
                } else {
                    emvCallback.setCallBackResult(pinData[0]);
                }
                return;
            }
            int result;
            if (isOnline) {
                result = emvProcessListener.onCardHolderPwd(true, isSelectedAIDSupportPINByPass, remainCnt, null);
            } else {
                result = emvProcessListener.onCardHolderPwd(false, isSelectedAIDSupportPINByPass, this.tmpRemainCount, pinData);
                this.tmpRemainCount--;
            }
            LogUtils.w(TAG, "emvGetHolderPwd,result:" + result);
            emvCallback.setCallBackResult(result);
        }

        @Override
        public void emvAdviceProc() {
            LogUtils.i(TAG, "emvAdviceProc");
        }

        @Override
        public void emvVerifyPINOK() {
            LogUtils.i(TAG, "EMV library verify PIN and passes, prompt PIN OK");
        }

        @Override
        public int emvUnknowTLVData(short tag, ByteArray data) {
            if (enableDebugLog) {
                LogUtils.i(TAG, "emvUnknowTLVData, tag: " + Integer.toHexString(tag) + " length:" + data.data.length);
            }
            //for prolin platform,the kernel will obtain the following Tags' value by this function
            switch (tag) {
                case 0x9A:  // Transaction Date
                    byte[] date = new byte[7];
                    DeviceManager.getInstance().getTime(date);
                    System.arraycopy(date, 1, data.data, 0, 3);
                    break;
                case (short) 0x9F1E:    // (IFD) Serial Number
                    byte[] sn = new byte[10];
                    DeviceManager.getInstance().readSN(sn);
                    System.arraycopy(sn, 0, data.data, 0, Math.min(data.data.length, sn.length));
                    break;
                case (short) 0x9F21:    // Transaction Time
                    byte[] time = new byte[7];
                    DeviceManager.getInstance().getTime(time);
                    System.arraycopy(time, 4, data.data, 0, 3);
                    break;
                case (short) 0x9F37:    // Unpredictable Number
                    byte[] random = new byte[4];
                    DeviceManager.getInstance().getRand(random, 4);
                    System.arraycopy(random, 0, data.data, 0, data.data.length);
                    break;
                case (short) 0xFF01:    // Last transaction amount of current PAN and PAN SN
                    Arrays.fill(data.data, (byte) 0x00);
                    break;
                default:
                    if (enableDebugLog) {
                        LogUtils.e(TAG, "unsupported tag " + Integer.toHexString(tag));
                    }
                    return -1;
            }
            data.length = data.data.length;
            return RetCode.EMV_OK;
        }

        @Override
        public void certVerify() {
            //            Cardholder credential verify(PBOC)
        }

        @Override
        public int emvSetParam() {
            //          This function is used to set some AID specific parameter after performing application selection and before GPO. Application can call EMVSetTLVData in this function to set these parameters.
            //          When the return value of this function is not EMV_OK, kernel will abort the current transaction.
            ByteArray byteArray = new ByteArray();
            int ret = EMVCallback.EMVGetTLVData((short) 0x4f, byteArray);
            LogUtils.d(TAG, "emvSetParam EMVGetTLVData ret =" + ret + "byteArray.length = " + byteArray.length);
            if (ret != RetCode.EMV_OK) {
                return RetCode.EMV_OK;
            }
            if (enableDebugLog) {
                LogUtils.d(TAG, "emvSetParam EMVGetTLVData ret =" + ConvertUtils.bcd2Str(byteArray.data));
            }
            byte[] aid = Arrays.copyOfRange(byteArray.data, 0, byteArray.length);
            if (enableDebugLog) {
                LogUtils.d(TAG, "emvSetParam aid =" + ConvertUtils.bcd2Str(aid));
            }
            resetParam(aid);
            return RetCode.EMV_OK;
        }

        @Override
        public int emvVerifyPINfailed(byte[] reserved) {
            return 0;
        }

        @Override
        public int cRFU2() {
            return 0;
        }

    }
}
