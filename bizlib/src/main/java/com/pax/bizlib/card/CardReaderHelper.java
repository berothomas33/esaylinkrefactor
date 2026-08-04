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
 * Date	                 Author	                Action
 * 20200921  	         xieYb                  Create
 * ===========================================================================================
 */
package com.pax.bizlib.card;

import android.os.ConditionVariable;
import android.text.TextUtils;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import com.pax.bizentity.entity.SearchMode;
import com.pax.bizlib.R;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.commonlib.utils.ResourceUtil;
import com.pax.configservice.export.ConfigKeyConstant;
import com.pax.configservice.export.ConfigServiceConstant;
import com.pax.configservice.export.IConfigParamService;
import com.pax.dal.IIcc;
import com.pax.dal.IMag;
import com.pax.dal.IPicc;
import com.pax.dal.entity.EDetectMode;
import com.pax.dal.entity.EPiccType;
import com.pax.dal.entity.EReaderType;
import com.pax.dal.entity.PiccCardInfo;
import com.pax.dal.exceptions.EIccDevException;
import com.pax.dal.exceptions.EMagDevException;
import com.pax.dal.exceptions.EPiccDevException;
import com.pax.dal.exceptions.IccDevException;
import com.pax.dal.exceptions.MagDevException;
import com.pax.dal.exceptions.PiccDevException;
import com.pax.emvservice.export.constant.EmvServiceConstant;
import com.pax.emvservice.export.mag.IMagCardService;
import com.pax.poslib.model.ModelInfo;
import com.pax.poslib.neptune.Sdk;
import com.sankuai.waimai.router.Router;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The type Card reader helper.
 */
@MainThread
public class CardReaderHelper{
    private static final String TAG = "CardReaderHelper";
    private final Callback callback;

    private final IIcc icc = Sdk.getInstance().getDal(BaseApplication.getAppContext()).getIcc();
    private final IMag mag = Sdk.getInstance().getDal(BaseApplication.getAppContext()).getMag();
    private final IPicc internalPicc = Sdk.getInstance().getDal(BaseApplication.getAppContext()).getPicc(EPiccType.INTERNAL);
    private final IPicc exPicc = Sdk.getInstance().getDal(BaseApplication.getAppContext()).getPicc(EPiccType.EXTERNAL);
    private boolean isSupportMag = false;
    private boolean isSupportIcc = false;
    private boolean isSupportInternalPicc = false;
    private boolean isSupportExPicc = false;

    private boolean isMagDisabled = false;
    private boolean isIccDisabled = false;
    private boolean isInternalPiccDisabled = false;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isSwiped = new AtomicBoolean(false);
    private final AtomicInteger detectResult = new AtomicInteger(-1);

    private final IConfigParamService configParamService = Router.getService(IConfigParamService.class, ConfigServiceConstant.CONFIGSERVICE_CONFIG);

    private ConditionVariable cv;
    private long iccUnexpectedTime = -1;

    public CardReaderHelper(@NonNull Callback callback) {
        this.callback = callback;
    }

    /**
     * Polling.
     *
     * @param readType the read type
     */
    public void polling(byte readType){
        if (configParamService.getBoolean(ConfigKeyConstant.EDC_SOLVE_IMAG_CLS_CONFLICT, false)){
            cv = new ConditionVariable();
        }
        isRunning.set(true);
        isSwiped.set(false);
        detectResult.set(-1);
        startDetect(readType);
    }

    private void startDetect(byte readType) {
        isSupportMag = SearchMode.isSupportMag(readType);
        isSupportIcc = SearchMode.isSupportIcc(readType);
        isSupportExPicc = SearchMode.isSupportExternalPicc(readType);
        isSupportInternalPicc = SearchMode.isSupportInternalPicc(readType);
        if (isSupportMag || isSupportIcc || isSupportExPicc){
            BaseApplication.getAppContext().runInBackground(new DetechMagIccExPicc());
        }
        if (isSupportInternalPicc){
            BaseApplication.getAppContext().runInBackground(new DetechInternalPicc());
        }
    }


    private class DetechMagIccExPicc implements Runnable {

        /**
         * When an object implementing interface <code>Runnable</code> is used
         * to create a thread, starting the thread causes the object's
         * <code>run</code> method to be called in that separately executing
         * thread.
         * <p>
         * The general contract of the method <code>run</code> is that it may
         * take any action whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {
            if (isSupportMag){
                try {
                    mag.close();
                    mag.open();
                    mag.reset();
                } catch (MagDevException e) {
                    callback.onError(String.format(Locale.US, "%s%s", e.getErrModule(), e.getErrMsg()));
                    LogUtils.e(e);
                }
            }
            if (isSupportExPicc){
                try {
                    exPicc.close();
                    exPicc.open();
                } catch (PiccDevException e) {
                    callback.onError(String.format(Locale.US, "%s%s", e.getErrModule(), e.getErrMsg()));
                    LogUtils.e(TAG, e);
                }
            }
            if (isSupportIcc){
                try {
                    icc.close((byte) 0);
                } catch (IccDevException e) {
                    callback.onError(String.format(Locale.US, "%s%s", e.getErrModule(), e.getErrMsg()));
                    LogUtils.e(TAG, e);
                }
            }
            while (isRunning.get()){
                if (isSupportMag && !isMagDisabled){
                    try {
                        if (mag.isSwiped()){
                            isSwiped.set(true);
                            IMagCardService magCardService = Router.getService(IMagCardService.class, EmvServiceConstant.EMVSERVICE_MAG_CARD);
                            magCardService.magRead();
                            if (TextUtils.isEmpty(magCardService.getTrack2())) {
                                LogUtils.i(TAG, "track data is null");
                                continue;
                            }
                            if (isRunning.get()){
                                detectResult.set(SearchMode.SWIPE);
                                isRunning.set(false);
                                if (cv != null){
                                    cv.open();
                                }
                                callback.onMagDetected(magCardService.getTrack1(),
                                        magCardService.getTrack2(),
                                        magCardService.getTrack3());
                                configParamService.putInt(ConfigKeyConstant.RESULT_READER_TYPE, EReaderType.MAG.ordinal());
                                break;
                            }
                        }
                    } catch (MagDevException e) {
                        LogUtils.e(TAG, e);
                        //close the mag function from pax store
                        if (e.getErrCode() == EMagDevException.ERROR_DISABLED.getErrCodeFromBasement()) {
                            isMagDisabled = true;
                            //here needn't to update isSupportMag as false for the final mag close.
                            callback.onMagDisable();
                        }
                    }
                }
                if (isSupportIcc && !isIccDisabled){
                    try {
                        if (icc.detect((byte) 0)){
                            byte[] res = icc.init((byte) 0);
                            if (res != null){
                                detectResult.set(SearchMode.INSERT);
                                isRunning.set(false);
                                if (cv != null){
                                    cv.open();
                                }
                                callback.onIccDetected();
                                configParamService.putInt(ConfigKeyConstant.RESULT_READER_TYPE, EReaderType.ICC.ordinal());
                                break;
                            } else {
                                LogUtils.w(TAG,"Failed to Reset IC card and return the content of answer to reset");
                                //notice to insert again or swipe
                                if (ModelInfo.getInstance().isMagIccConflict()) {
                                    callback.onError(ResourceUtil.getString(R.string.icc_error_swipe_card));
                                }
                            }
                        } else {
                            iccUnexpectedTime = -1;
                        }
                    } catch (IccDevException e) {
                        LogUtils.e(e);
                        if (e.getErrCode() == EIccDevException.DEVICES_ERR_UNEXPECTED.getErrCodeFromBasement()) {
                            LogUtils.w(TAG,"IC card unExcepted exception,for example,Card inserted upside down");
                            if (iccUnexpectedTime > 0 && System.currentTimeMillis() - iccUnexpectedTime > 3000) {
                                // Go to EMV process and let it fallback
                                detectResult.set(SearchMode.INSERT);
                                isRunning.set(false);
                                if (cv != null) {
                                    cv.open();
                                }
                                callback.onIccDetected();
                                configParamService.putInt(ConfigKeyConstant.RESULT_READER_TYPE, EReaderType.ICC.ordinal());
                                iccUnexpectedTime = -1;
                                break;
                            } else {
                                if (iccUnexpectedTime < 0) {
                                    iccUnexpectedTime = System.currentTimeMillis();
                                }
                                callback.onError(ResourceUtil.getString(R.string.icc_error_swipe_card));
                            }
                        }
                        //close icc function from pax store
                        if (e.getErrCode() == EIccDevException.ERROR_DISABLED.getErrCodeFromBasement()) {
                            isIccDisabled = true;
                            //here needn't to update isSupportIcc as false for the final ic close.
                            callback.onIccDisable();
                        }
                    }
                }
                if (isSupportExPicc){
                    try {
                        PiccCardInfo exPiccInfo = exPicc.detect(EDetectMode.EMV_AB);
                        if (exPiccInfo != null){
                            detectResult.set(SearchMode.EXTERNAL_WAVE);
                            isRunning.set(false);
                            if (cv != null){
                                cv.open();
                            }
                            callback.onExPiccDetected(exPiccInfo.getSerialInfo());
                            configParamService.putInt(ConfigKeyConstant.RESULT_READER_TYPE, EReaderType.PICCEXTERNAL.ordinal());
                            break;
                        }
                    } catch (PiccDevException e) {
                        LogUtils.e(TAG,e);
                    }
                }
            }
            if (isSupportMag && detectResult.get() == SearchMode.SWIPE) {
                try {
                    LogUtils.d(TAG, "==============mag.close();=================");
                    mag.close();
                } catch (Exception e) {
                    LogUtils.e(TAG, e);
                }
            }

            if (isSupportIcc && detectResult.get() == SearchMode.SWIPE) {
                try {
                    LogUtils.d(TAG, "==============icc.close();=================");
                    icc.close((byte) 0);
                } catch (Exception e) {
                    LogUtils.e(TAG, e);
                }
            }

            if (isSupportExPicc && detectResult.get() == SearchMode.SWIPE) {
                try {
                    LogUtils.d(TAG, "==============piccExternal.close();=================");
                    exPicc.close();
                } catch (Exception e) {
                    LogUtils.e(TAG, e);
                }
            }
        }
    }

    private class DetechInternalPicc implements Runnable {
        /**
         * When an object implementing interface <code>Runnable</code> is used
         * to create a thread, starting the thread causes the object's
         * <code>run</code> method to be called in that separately executing
         * thread.
         * <p>
         * The general contract of the method <code>run</code> is that it may
         * take any action whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {
            try {
                internalPicc.close();
                LogUtils.d(TAG,"internalPicc ready to open");
                internalPicc.open();
            } catch (PiccDevException e) {
                callback.onError(String.format(Locale.US, "%s%s", e.getErrModule(), e.getErrMsg()));
                LogUtils.e(TAG, e);
            }

            int openCounter = 0;
            while (isRunning.get() && !isInternalPiccDisabled){
                try {
                    PiccCardInfo internalPiccInfo = internalPicc.detect(EDetectMode.EMV_AB);
                    if (cv != null){
                        cv.block(350);
                    }
                    if (internalPiccInfo != null && !isSwiped.get()){
                        detectResult.set(SearchMode.INTERNAL_WAVE);
                        isRunning.set(false);
                        callback.onPiccDetected(internalPiccInfo.getSerialInfo());
                        configParamService.putInt(ConfigKeyConstant.RESULT_READER_TYPE, EReaderType.PICC.ordinal());
                        break;
                    }

                } catch (PiccDevException e) {
                    LogUtils.e(TAG,e);
                    if (e.getErrCode() == EPiccDevException.ERROR_DISABLED.getErrCodeFromBasement()) {
                        isInternalPiccDisabled = true;
                        //here needn't to update isSupportInternalPicc as false for the final picc close.
                        callback.onPiccDisable();
                        break;
                    } else if (e.getErrCode() == EPiccDevException.PICC_ERR_NOT_OPEN.getErrCodeFromBasement()) {
                        if (openCounter++ < 2) {
                            try {
                                internalPicc.open();
                                LogUtils.d(TAG, "Try open again. Retries remaining: " + (2 - openCounter));
                            } catch (Exception exception) {
                                LogUtils.e(TAG, exception);
                                callback.onPiccDisable();
                                break;
                            }
                        } else {
                            callback.onError(e.getErrMsg());
                            callback.onPiccDisable();
                            break;
                        }
                    } else {
                        callback.onError(e.getErrMsg());
                        callback.onPiccDisable();
                        break;
                    }
                }
            }
            if (cv != null){
                cv.close();
            }
            //close internal picc except when the contactless card is detected
            if ( isSupportInternalPicc && detectResult.get() == SearchMode.SWIPE) {

                try {
                    LogUtils.d(TAG, "==============piccInternal.close();=================");
                    internalPicc.close();
                } catch (Exception e) {
                    LogUtils.e(TAG, e.getMessage());
                }
            }
        }
    }

    /**
     * stop polling,all the reader will be closed except the the mode saved in detectResult
     * for example,if the detectResult is SearchMode.INSERT,that means IC card has been detected,
     * it needed to stay open until emv process finished,so don't forget to close IC after emv process
     * finished,PICC is the same.
     */
    public void stopPolling() {
        isRunning.set(false);
    }

    public interface Callback {
        /**
         * On Mag detect disable
         */
        default void onMagDisable() {
            // do nothing
        }

        /**
         * On Icc detect disable
         */
        default void onIccDisable() {
            // do nothing
        }

        /**
         * On Picc detect disable
         */
        default void onPiccDisable() {
            // do nothing
        }

        /**
         * On detect error
         *
         * @param message Error message
         */
        default void onError(String message) {
            // do nothing
        }

        /**
         * On Mag detected
         *
         * @param trackData1 track data 1
         * @param trackData2 track data 2
         * @param trackData3 track data 3
         */
        default void onMagDetected(String trackData1, String trackData2, String trackData3) {
            // do nothing
        }

        /**
         * On Icc detected
         */
        default void onIccDetected() {
            // do nothing
        }

        /**
         * On Picc detected
         *
         * @param serialInfo Card serial number information
         */
        default void onPiccDetected(byte[] serialInfo) {
            // do nothing
        }

        /**
         * On External Picc detected
         *
         * @param serialInfo Card serial number information
         */
        default void onExPiccDetected(byte[] serialInfo) {
            // do nothing
        }
    }
}
