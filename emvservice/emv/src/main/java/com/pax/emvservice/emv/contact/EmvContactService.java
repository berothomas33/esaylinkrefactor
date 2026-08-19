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
 * 20210610 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.emv.contact;

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
import com.pax.emvbase.process.contact.IContactCallback;
import com.pax.emvbase.process.entity.EOnlineResult;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.OnlineResultWrapper;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvlib.process.contact.EmvProcess;
import com.pax.emvservice.export.constant.EmvServiceConstant;
import com.pax.emvservice.export.contact.IEmvContactService;
import com.pax.emvservice.export.contact.IContactResultListener;
import com.pax.jemv.clcommon.RetCode;
import com.sankuai.waimai.router.annotation.RouterService;

@RouterService(interfaces = IEmvContactService.class,key = EmvServiceConstant.EMVSERVICE_CONTACT,singleton = true)
public class EmvContactService implements IEmvContactService {
    private static final String TAG = "EmvContactService";

    private TransResult transResult;
    private String cachedTrack2Data = null;
    private boolean userCancel;
    private boolean timeOut;

    /**
     * user cancel during contact process
     *
     * @param userCancel userCancel
     */
    @Override
    public void setUserCancel(boolean userCancel) {
        this.userCancel = userCancel;
    }

    /**
     * application timeout,and finish the emv process
     *
     * @param isTimeOut isTimeOut
     */
    @Override
    public void timeOut(boolean isTimeOut) {
        this.timeOut = isTimeOut;
    }

    /**
     * check whether application is timeout
     */
    @Override
    public boolean isTimeOut() {
        return timeOut;
    }

    /**
     * check whether user cancel
     */
    @Override
    public boolean isUserCancel() {
        return userCancel;
    }

    /**
     * emv pretreatment
     *
     * @param emvProcessParam emvProcessParam
     * @return pretreatment result
     */
    @Override
    public int preTransProcess(EmvProcessParam emvProcessParam) {
        return EmvProcess.getInstance().preTransProcess(emvProcessParam);
    }

    /**
     * EMV application selection only. Must be called, and must succeed, before
     * {@link #startTransProcess(IContactCallback)}.
     *
     * @param contactCallback contactCallback
     * @return emv l2 lib api return code (RetCode.EMV_OK on success)
     */
    @Override
    public int selectApplication(IContactCallback contactCallback) {
        //reset every transaction
        cachedTrack2Data = null;
        timeOut(false);
        setUserCancel(false);
        EmvProcess.getInstance().registerEmvProcessListener(contactCallback);
        transResult = EmvProcess.getInstance().selectApplication();
        int resultCode = transResult.getResultCode();
        if (resultCode != RetCode.EMV_OK) {
            // Selection failed — the transaction ends here; release the listener now since
            // startTransProcess() (and its own unregister in finally) will never run.
            EmvProcess.getInstance().registerEmvProcessListener(null);
        }
        return resultCode;
    }

    /**
     * continue contact process after a successful {@link #selectApplication(IContactCallback)}
     * call,need handle timeout situation
     *
     * @param contactCallback contactCallback
     * @return result
     */
    @Override
    public int startTransProcess(IContactCallback contactCallback) {
        try{
            EmvProcess.getInstance().registerEmvProcessListener(contactCallback);
            transResult = EmvProcess.getInstance().startTransProcess();
            int resultCode = transResult.getResultCode();
            TransResultEnum transResultEnum = transResult.getTransResult();
            if(resultCode != RetCode.EMV_OK){
                return resultCode;
            }
            //check whether need goes online
            if (transResultEnum == TransResultEnum.RESULT_REQ_ONLINE){
                //start online
                OnlineResultWrapper onlineResultWrapper = contactCallback.startOnlineProcess();
                IssuerRspData issuerRspData = onlineResultWrapper.getIssuerRspData();
                int onlineResultCode = onlineResultWrapper.getResultCode();

                // Update result
                transResult.setResultCode(onlineResultWrapper.getResultCode());
                transResult.setTransResult(onlineResultWrapper.getTransResultEnum());

                /*
                 * //AET-146
                 * Whatever value it returns from startOnlineProcess(), 2nd GAC should be performed as per EMV Book 3
                 * The right way to fix AET-146 is to map ABORT_TERMINATED to ONLINE_FAILED so that
                 * EMVApi#EMVCompleteTrans will not return -30: emv param error
                 *
                 * should ensure script.length will not throw Null Pointer Exception
                 * If Field 39 responsed from host is not 00, EMVCallback.EMVCompleteTrans will return -11(EMV DENIAL)
                 *
                 * 1st param of EMVApi#EMVCompleteTrans only accept 3 values:
                 * ONLINE_APPROVE, ONLINE_DENIAL, and ONLINE_FAILED,
                 * reference to the API doc of JNI_EMV_LIB_v102
                 */
                TransResult secondTransResult = EmvProcess.getInstance().completeTransProcess(issuerRspData);
                transResult.setResultCode(secondTransResult.getResultCode());

                // Check result
                if (onlineResultCode == EOnlineResult.APPROVE.getResultCode()) {
                    // Online approved
                    if (secondTransResult.getTransResult() != TransResultEnum.RESULT_ONLINE_APPROVED) {
                        // but card denied
                        transResult.setTransResult(TransResultEnum.RESULT_ONLINE_CARD_DENIED);
                    } else {
                        // and card approved
                        transResult.setTransResult(TransResultEnum.RESULT_ONLINE_APPROVED);
                    }
                } else if (onlineResultCode == EOnlineResult.FAILED.getResultCode()) {
                    // Online failed
                    if (secondTransResult.getTransResult() != TransResultEnum.RESULT_ONLINE_APPROVED) {
                        // and card denied
                        transResult.setTransResult(TransResultEnum.RESULT_ONLINE_FAILED);
                    } else {
                        // but card approved
                        transResult.setTransResult(TransResultEnum.RESULT_ONLINE_FAILED_CARD_APPROVED);
                    }
                } else {
                    // Online denied
                    transResult.setTransResult(TransResultEnum.RESULT_ONLINE_DENIED);
                }
                return secondTransResult.getResultCode();
            }
            return 0;
        }finally {
            EmvProcess.getInstance().registerEmvProcessListener(null);
        }
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

    /**
     * check contact result
     * @param listener result callback
     */
    @Override
    public void checkContactResult(IContactResultListener listener) {
        int resultCode = transResult.getResultCode();
        TransResultEnum transResultEnum = transResult.getTransResult();
        CvmResultEnum cvmResult = transResult.getCvmResult();
        LogUtils.d(TAG, "check result: code = " + resultCode
                + ", enum = " + transResultEnum.name()
                + ", cvm = " + cvmResult.name());
        boolean isNeedSignature = cvmResult == CvmResultEnum.CVM_SIG || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG;
        switch (transResultEnum) {
            case RESULT_OFFLINE_APPROVED:
                // TC
                listener.offlineApproved(isNeedSignature, true);
                break;
            case RESULT_ONLINE_APPROVED:
                // ARQC -> ONLINE APPROVED -> TC
                listener.onlineApproved(isNeedSignature);
                break;
            case RESULT_ONLINE_CARD_DENIED:
                // ARQC -> ONLINE APPROVED -> AAC
                listener.onlineCardDenied(resultCode);
                break;
            case RESULT_ONLINE_FAILED_CARD_APPROVED:
                // ARQC -> ONLINE FAILED -> TC
                listener.offlineApproved(isNeedSignature, false);
                break;
            case RESULT_ONLINE_FAILED:
                // ARQC -> ONLINE FAILED -> AAC
                listener.onlineFailed();
                break;
            case RESULT_ONLINE_DENIED:
                // ARQC -> ONLINE DENIAL
                listener.onlineDenied();
                break;
            case RESULT_OFFLINE_DENIED:
                // AAC
                listener.offlineDenied(resultCode);
                break;
            case RESULT_SIMPLE_FLOW_END:
                listener.simpleFlowEnd();
                break;
            case RESULT_FALLBACK:
                listener.fallback();
                break;
            default:
                LogUtils.e(TAG, "Cannot handle this statement");
                listener.offlineDenied(resultCode);
                break;
        }
    }

    @Override
    public byte[] getTlv(int tag) {
        return EmvProcess.getInstance().getTlv(tag);
    }

    @Override
    public void setTlv(int tag, byte[] value) {
        EmvProcess.getInstance().setTlv(tag, value);
    }
}
