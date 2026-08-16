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
 * 20210513 	        xieYb                  Create
 * ===========================================================================================
 *
 */
package com.pax.bizlib.trans;

import android.os.Build;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.pax.bizlib.params.ParamHelper;
import com.pax.bizlib.ped.PedHelper;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.export.ConfigKeyConstant;
import com.pax.configservice.export.IConfigParamService;
import com.pax.configservice.impl.ConfigParamService;
import com.pax.dal.ICardReaderHelper;
import com.pax.dal.IDAL;
import com.pax.dal.IPed;
import com.pax.dal.entity.ECheckMode;
import com.pax.dal.entity.EPedDesMode;
import com.pax.dal.entity.EPedKeyType;
import com.pax.dal.entity.EPedMacMode;
import com.pax.dal.entity.EPinBlockMode;
import com.pax.dal.entity.EReaderType;
import com.pax.dal.entity.PollingResult;
import com.pax.dal.exceptions.EIccDevException;
import com.pax.dal.exceptions.EPiccDevException;
import com.pax.dal.exceptions.IccDevException;
import com.pax.dal.exceptions.PedDevException;
import com.pax.dal.exceptions.PiccDevException;
import com.pax.poslib.gl.convert.ConvertHelper;
import com.pax.poslib.neptune.Sdk;
import com.pax.poslib.utils.PosDeviceUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Device method
 */
public class Device {
    private static IConfigParamService configParamService = new ConfigParamService();
    private static IDAL idal = Sdk.getInstance().getDal(BaseApplication.getAppContext());
    private static final String TAG = "Device";

    private Device() {
        //do nothing
    }


    /**
     * get formatted date/time
     *
     * @param pattern date format
     * @return formatted data value
     */
    public static String getTime(String pattern) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern, Locale.US);
        return dateFormat.format(new Date());
    }

    /**
     * write TMK
     *
     * @param tmkIndex TMK index
     * @param tmkValue TMK value
     * @throws PedDevException exception
     */
    public static void writeTMK(int tmkIndex, byte[] tmkValue) throws PedDevException {
        // write TMK
        PedHelper.getPed().writeKey(EPedKeyType.TLK, (byte) 0,
                EPedKeyType.TMK, (byte) getMainKeyIndex(tmkIndex),
                tmkValue, ECheckMode.KCV_NONE, null);
    }

    /**
     * write TPK
     *
     * @param tpkValue TPK value
     * @param tpkKcv   TPK KCV
     * @throws PedDevException exception
     */
    public static void writeTPK(byte[] tpkValue, byte[] tpkKcv) throws PedDevException {
        int mKeyIndex = getMainKeyIndex(configParamService.getInt(ConfigKeyConstant.MK_INDEX));
        ECheckMode checkMode = ECheckMode.KCV_ENCRYPT_0;
        if (tpkKcv == null || tpkKcv.length == 0) {
            checkMode = ECheckMode.KCV_NONE;
        }
        PedHelper.getPed().writeKey(EPedKeyType.TMK, (byte) mKeyIndex,
                EPedKeyType.TPK, PosDeviceUtils.INDEX_TPK, tpkValue, checkMode, tpkKcv);
    }


    /**
     * write TAK
     *
     * @param takValue TAK value
     * @param takKcv   TAK KCV
     * @throws PedDevException exception
     */
    public static void writeTAK(byte[] takValue, byte[] takKcv) throws PedDevException {
        int mKeyIndex = getMainKeyIndex(configParamService.getInt(ConfigKeyConstant.MK_INDEX));
        ECheckMode checkMode = ECheckMode.KCV_ENCRYPT_0;
        if (takKcv == null || takKcv.length == 0) {
            checkMode = ECheckMode.KCV_NONE;
        }
        PedHelper.getPed().writeKey(EPedKeyType.TMK, (byte) mKeyIndex,
                EPedKeyType.TAK, PosDeviceUtils.INDEX_TAK, takValue, checkMode, takKcv);
    }


    /**
     * write TDK 
     *
     * @param tdkValue TDK value
     * @param tdkKcv   TDK KCV
     * @throws PedDevException exception
     */
    public static void writeTDK(byte[] tdkValue, byte[] tdkKcv) throws PedDevException {
        int mKeyIndex = getMainKeyIndex(configParamService.getInt(ConfigKeyConstant.MK_INDEX));
        ECheckMode checkMode = ECheckMode.KCV_ENCRYPT_0;
        if (tdkKcv == null || tdkKcv.length == 0) {
            checkMode = ECheckMode.KCV_NONE;
        }
        PedHelper.getPed().writeKey(EPedKeyType.TMK, (byte) mKeyIndex,
                EPedKeyType.TDK, PosDeviceUtils.INDEX_TDK, tdkValue, checkMode, tdkKcv);
    }

    /**
     * calculate PIN block
     *
     * @param panBlock      shifted pan block
     * @param supportBypass the support bypass
     * @param landscape     the landscape
     * @return PIN block
     * @throws PedDevException exception
     * @deprecated It has now been replaced by the method in PinService, because this method
     * cannot support both standard EMV and P2PE EMV.
     */
    @Deprecated
    public static byte[] getPinBlock(String panBlock, boolean supportBypass, boolean landscape) throws PedDevException {
        String tpk = configParamService.getString(ConfigKeyConstant.PK_VALUE);
        if (tpk != null && !tpk.isEmpty()) {
            writeTPK(ConvertHelper.getConvert().strToBcdPaddingRight(tpk), null);
        }
        IPed ped = PedHelper.getPed();
        String pinLen = "4,5,6,7,8,9,10,11,12";
        if (supportBypass) {
            pinLen = "0," + pinLen;
        }
        //外置TpyeA协议只需设置最小、最大长度
        if (ParamHelper.isExternalTypeAPed()){
            pinLen = "4,12";
        }
        if (ParamHelper.isInternalPed()){
            ped.setKeyboardLayoutLandscape(landscape);//设置密码键盘横向显示。仅支持EPedType.INTERNAL 类型。
        }

        return ped.getPinBlock(PosDeviceUtils.INDEX_TPK, pinLen, panBlock.getBytes(), EPinBlockMode.ISO9564_0, 60 * 1000);
    }

    /**
     * erase all keys
     *
     * @return the boolean
     */
    public static boolean eraseKeys() {
        try {
            return PedHelper.getPed().erase();
        } catch (PedDevException e) {
            LogUtils.e(TAG, "", e);
        }
        return false;
    }

    /**
     * calculate MAC with TAK
     *
     * @param data input data
     * @return MAC value
     * @throws PedDevException the ped dev exception
     */
    @NonNull
    public static byte[] calcMac(String data) throws PedDevException {
        String tak = configParamService.getString(ConfigKeyConstant.AK_VALUE);
        if (tak != null && !tak.isEmpty()) {
            writeTAK(ConvertHelper.getConvert().strToBcdPaddingRight(tak), null);
        }
        IPed ped = PedHelper.getPed();
        return ped.getMac(PosDeviceUtils.INDEX_TAK, data.getBytes(), EPedMacMode.MODE_00);
    }

    /**
     * Get mac byte [ ].
     *
     * @param data the data
     * @return the byte [ ]
     * @throws PedDevException the ped dev exception
     */
    public static byte[] getMac(byte[] data) throws PedDevException {
        String beforeCalcMacData = ConvertHelper.getConvert().bcdToStr(data);

        byte[] mac = Device.calcMac(beforeCalcMacData);
        if (mac.length > 0) {
            return ConvertHelper.getConvert().bcdToStr(mac).substring(0, 8).getBytes();
        }
        return "".getBytes();
    }

    /**
     * calculate DES with TDK
     *
     * @param data input data
     * @return DES value
     * @throws PedDevException exception
     */
    public static byte[] calcDes(byte[] data) throws PedDevException {
        String tdk = configParamService.getString(ConfigKeyConstant.DK_VALUE);
        if (tdk != null && !tdk.isEmpty()) {
            writeTDK(ConvertHelper.getConvert().strToBcdPaddingRight(tdk), null);
        }
        IPed ped = PedHelper.getPed();
        return ped.calcDes(PosDeviceUtils.INDEX_TDK, data, EPedDesMode.ENCRYPT);
    }

    /**
     * remove card listener, for showing message which polling
     */
    public interface RemoveCardListener {
        /**
         * On show msg.
         *
         * @param result the result
         */
        void onShowMsg(PollingResult result);
    }

    /**
     * force to remove card with prompting message
     *
     * @param listener remove card listener
     */
    public static void removeCard(RemoveCardListener listener) {
        boolean needShow = true;
        ICardReaderHelper helper = idal.getCardReaderHelper();

        EReaderType readerType = EReaderType.ICC_PICC;
        if (ParamHelper.isClssExternalResult()){
            readerType = EReaderType.ICC_PICCEXTERNAL;
        }

        while (true) {
            try {
                PollingResult result;
                while ((result = helper.polling(readerType, 100, true)).getOperationType()
                        == PollingResult.EOperationType.OK) {
                    // remove card prompt
                    if (listener != null && needShow) {
                        needShow = false;
                        listener.onShowMsg(result);
                    }
                    SystemClock.sleep(500);
                    BaseApplication.getAppContext().runInBackground(PosDeviceUtils::beepErr);
                }
                configParamService.putInt(ConfigKeyConstant.RESULT_READER_TYPE, EReaderType.DEFAULT.ordinal());
                return;
            } catch (IccDevException e) {
                LogUtils.e(TAG, "", e);
                if (e.getErrCode() != EIccDevException.ERROR_DISABLED.getErrCodeFromBasement()) {
                    return;
                }
                readerType = ParamHelper.isClssExternalResult() ? EReaderType.PICCEXTERNAL : EReaderType.PICC;
            } catch (PiccDevException e) {
                LogUtils.e(TAG, "", e);
                if (e.getErrCode() != EPiccDevException.ERROR_DISABLED.getErrCodeFromBasement()) {
                    return;
                }
                readerType = EReaderType.ICC;
            } catch (Exception e) {
                LogUtils.e(TAG, "", e);
                return;
            }
        }
    }

    /**
     * 得到设备的型号
     *
     * @return device model
     */
    public static String getDeviceModel() {
        return Build.MODEL.toUpperCase(); //机器型号
    }
    
    /**
     * 获取主秘钥索引
     *
     * @param index 0~99的主秘钥索引值
     * @return 1 ~100的主秘钥索引值
     */
    public static int getMainKeyIndex(int index) {
        return index + 1;
    }
}
