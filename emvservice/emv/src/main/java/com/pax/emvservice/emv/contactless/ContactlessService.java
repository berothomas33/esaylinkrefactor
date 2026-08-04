/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2019-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date	                Author	               Action
 * 20210618 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.emv.contactless;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.pax.bizentity.entity.Issuer;
import com.pax.bizlib.card.PanUtils;
import com.pax.bizlib.card.TrackUtils;
import com.pax.bizlib.trans.AcqManager;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.constant.TagsTable;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.process.contactless.IContactlessCallback;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.OnlineResultWrapper;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvlib.process.contactless.ClssProcess;
import com.pax.emvservice.export.constant.EmvServiceConstant;
import com.pax.emvservice.export.contactless.IEmvContactlessService;
import com.pax.emvservice.export.contactless.IContactlessResultListener;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.emv.api.EMVApi;
import com.pax.jemv.emv.model.EmvParam;
import com.sankuai.waimai.router.annotation.RouterService;

import static com.pax.jemv.clcommon.RetCode.EMV_OK;
@RouterService(interfaces = IEmvContactlessService.class,key = EmvServiceConstant.EMVSERVICE_CONTACTLESS,singleton = true)
public class ContactlessService implements IEmvContactlessService {
    private TransResult transResult;
    private String cachedTrack2Data = null;
    private int flowType;
    private boolean isLastNeedSeePhone = false;
    /**
     * emv contactless pretreatment
     * @param emvProcessParam emvProcessParam
     * @return pretreatment result
     */
    @Override
    public int preTransProcess(EmvProcessParam emvProcessParam) {
        flowType = emvProcessParam.getEmvTransParam().getFlowType();
        return ClssProcess.getInstance().preTransProcess(emvProcessParam);
    }

    /**
     * start contactless process,need handle timeout situation
     * @param contactlessCallback contactlessCallback
     * @return result
     */
    @Override
    public int startTransProcess(IContactlessCallback contactlessCallback) {
        try{
            //reset every transaction
            cachedTrack2Data = null;
            ClssProcess.getInstance().registerClssProcessListener(contactlessCallback);
            transResult = ClssProcess.getInstance().startTransProcess();
            CvmResultEnum cvmResult = transResult.getCvmResult();
            int resultCode = transResult.getResultCode();
            TransResultEnum transResultEnum = transResult.getTransResult();
            if(resultCode != RetCode.EMV_OK){
                return resultCode;
            }
            int ret = contactlessCallback.confirmCard();
            LogUtils.d("Contactless", "confirm card: " + ret);
            if (ret != RetCode.EMV_OK){//for example,timeout/data_error
                transResult = new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
                return ret;
            }

            if (flowType == EmvTransParam.FLOWTYPE_SIMPLE){
                transResult = new TransResult(0, TransResultEnum.RESULT_SIMPLE_FLOW_END, CvmResultEnum.CVM_NO_CVM);
                return 0;
            }
            if (cvmResult == CvmResultEnum.CVM_ONLINE_PIN || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG){
                ret = contactlessCallback.onCardHolderPwd(true, true, 0, null);
                // Because PIN Bypass is supported, it is necessary to exclude the case where the
                // returned result is NO PASSWORD.
                if (ret != RetCode.EMV_OK && ret != RetCode.EMV_NO_PASSWORD) {
                    transResult = new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, cvmResult);
                    return ret;
                }
            }
            //check whether need goes online
            if (transResultEnum == TransResultEnum.RESULT_REQ_ONLINE){
                //start online
                OnlineResultWrapper onlineResultWrapper = contactlessCallback.startOnlineProcess();
                IssuerRspData issuerRspData = onlineResultWrapper.getIssuerRspData();
                TransResultEnum onlineTransResultEnum = onlineResultWrapper.getTransResultEnum();
                //handle online result after second GAC
                if (TransResultEnum.RESULT_ONLINE_APPROVED != onlineTransResultEnum){
                    transResult.setTransResult(onlineTransResultEnum);
                    transResult.setResultCode(onlineTransResultEnum.ordinal());
                    return onlineTransResultEnum.ordinal();
                }
                boolean needSecondTap = ClssProcess.getInstance().isNeedSecondTap(issuerRspData);
                if (needSecondTap){
                    contactlessCallback.onDetect2ndTap();
                    transResult = ClssProcess.getInstance().completeTransProcess(issuerRspData);
                }else {
                    transResult = new TransResult(EMV_OK, TransResultEnum.RESULT_ONLINE_APPROVED, CvmResultEnum.CVM_NO_CVM);
                }
                // restore signature
                if (cvmResult == CvmResultEnum.CVM_SIG || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG) {
                    transResult.setCvmResult(CvmResultEnum.CVM_SIG);
                }
            }
            return transResult.getResultCode();
        }finally {
            ClssProcess.getInstance().unregisterClssProcessListener();
        }

    }

    /**
     * Gets kernel type
     *
     * @return kernel type
     */
    @Override
    public int getKernelType() {
        return ClssProcess.getInstance().getKernType().kernType;
    }

    /**
     * check contactless result
     * @param clsResultListener clsResultListener
     */
    @Override
    public void checkClsResult(IContactlessResultListener clsResultListener) {
        int resultCode = transResult.getResultCode();
        TransResultEnum transResultEnum = transResult.getTransResult();
        CvmResultEnum cvmResult = transResult.getCvmResult();
        LogUtils.d("Contactless", "result code: " + resultCode
                + ", trans result: " + transResultEnum.name()
                + ", cvm result: " + cvmResult.name());
        if (resultCode == RetCode.EMV_OK){
            if (cvmResult == CvmResultEnum.CVM_CONSUMER_DEVICE){
                isLastNeedSeePhone = true;
                clsResultListener.seePhone();
                return;
            }
            if (transResult.getTransResult() == TransResultEnum.RESULT_OFFLINE_APPROVED){
                clsResultListener.offlineApproved(cvmResult == CvmResultEnum.CVM_SIG);
            }else if (transResult.getTransResult() == TransResultEnum.RESULT_ONLINE_APPROVED){
                clsResultListener.onlineApproved(cvmResult == CvmResultEnum.CVM_SIG);
            }else if (transResultEnum == TransResultEnum.RESULT_SIMPLE_FLOW_END){
                clsResultListener.simpleFlowEnd();
            } else {
                // Unknown status
                clsResultListener.offlineDenied(RetCode.CLSS_DECLINE);
            }
        }else {
            if (transResultEnum == TransResultEnum.RESULT_CLSS_SEE_PHONE) { //contactless
                isLastNeedSeePhone = true;
                clsResultListener.seePhone();
            } else if (transResultEnum == TransResultEnum.RESULT_CLSS_TRY_ANOTHER_INTERFACE
                    || transResult.getResultCode() == RetCode.CLSS_USE_CONTACT) {//contactless
                //restart detect icc card and transaction
                clsResultListener.tryAnotherInterface();
            } else if (transResultEnum == TransResultEnum.RESULT_TRY_AGAIN) {//contactless
                //PICC return  USE_CONTACT 1.restart detect card and transaction
                clsResultListener.tryAgain();
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_DENIED){
                //host reject，prompt has been showed during online
                clsResultListener.onlineDenied();
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_FAILED){
                //such as connect error、receive error、pack error，prompt has been showed during online
                clsResultListener.onlineFailed();
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_CARD_DENIED){
                clsResultListener.onlineCardDenied(resultCode);
            } else if (transResultEnum == TransResultEnum.RESULT_OFFLINE_DENIED){
                //here we need prompt details by resultCode
                clsResultListener.offlineDenied(resultCode);
            } else {
                // Unknown status
                clsResultListener.offlineDenied(resultCode);
            }
        }
    }

    /**
     * Get capability from EmvParam
     *
     * @return capability
     */
    @Override
    public byte[] getCapability() {
        EmvParam emvParam = new EmvParam();
        EMVApi.EMVGetParameter(emvParam);
        return emvParam.capability;
    }

    /**
     * Return if the last trans returned "Please See Phone" warning
     *
     * @return boolean value
     */
    @Override
    public boolean getIsLastNeedSeePhone() {
        return isLastNeedSeePhone;
    }

    /**
     * Sets isLastNeedSeePhone
     *
     * @param isLastNeedSeePhone isLastNeedSeePhone
     */
    @Override
    public void setIsLastNeedSeePhone(boolean isLastNeedSeePhone) {
        this.isLastNeedSeePhone = isLastNeedSeePhone;
    }

    @Override
    public byte[] getTlv(int tag) {
        return ClssProcess.getInstance().getTlv(tag);
    }

    @Override
    public void setTlv(int tag, byte[] value) {
        ClssProcess.getInstance().setTlv(tag, value);
    }

    /**
     * Gets pan, the result is ciphertext in p2pe mode
     *
     * @return pan
     */
    @NonNull
    @Override
    public String getPan() {
        if (!TextUtils.isEmpty(cachedTrack2Data)){
            return TrackUtils.getPan(cachedTrack2Data);
        }
        String track2Data = getTrack2Data();
        if (!TextUtils.isEmpty(track2Data)){
            return TrackUtils.getPan(track2Data);
        }
        //some cards don't have track2 data
        byte[] panBytes= getTlv(TagsTable.PAN);
        String pan = ConvertUtils.bcd2Str(panBytes, panBytes.length);
        int indexF = pan.indexOf('F');
        return pan.substring(0, indexF != -1 ? indexF : pan.length());
    }

    /**
     * Gets pan block
     *
     * @return pan block
     */
    @NonNull
    @Override
    public String getPanBlock() {
        return PanUtils.getPanBlock(getPan(), PanUtils.X9_8_WITH_PAN);
    }

    /**
     * Gets masked pan
     *
     * @param pattern masked pattern
     * @return masked pan
     */
    @Override
    public String getMaskedPan(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return PanUtils.maskCardNo(getPan());
        }
        return PanUtils.maskCardNo(getPan(),pattern);
    }

    /**
     * Gets expire Date
     *
     * @return expire Date
     */
    @Override
    public String getExpireDate() {
        byte[] expireDate = getTlv(TagsTable.EXPIRE_DATE);
        if (expireDate !=null && expireDate.length >0){
            String tmp = ConvertUtils.bcd2Str(expireDate);
            return tmp.substring(0,4);
        }
        return "";
    }

    /**
     * Gets cardholder name
     *
     * @return cardholder name
     */
    @Override
    public String getCardholderName() {
        byte[] cardholderName = getTlv(TagsTable.CARDHOLDER_NAME);
        return ConvertUtils.bcd2Str(cardholderName);
    }

    /**
     * Gets Issuer by pan(use maskedPan in p2pe mode)
     *
     * @return Issuer
     */
    @Override
    public Issuer getMatchedIssuerByPan() {
        return AcqManager.getInstance().findIssuerByPan(getPan());
    }

    /**
     * Gets track2 data
     *
     * @return track2 data,the result is ciphertext in p2pe mode
     */
    @Override
    public String getTrack2Data() {
        cachedTrack2Data = TrackUtils.getTrack2FromTag57(getTlv(TagsTable.TRACK2));
        return cachedTrack2Data;
    }
}
