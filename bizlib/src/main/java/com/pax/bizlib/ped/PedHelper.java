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
 * 20200318  	         xieYb                  Create
 * ===========================================================================================
 */
package com.pax.bizlib.ped;

import com.pax.bizlib.params.ParamHelper;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IDAL;
import com.pax.dal.IPed;
import com.pax.dal.entity.ECheckMode;
import com.pax.dal.entity.EPedKeyType;
import com.pax.dal.entity.EPedMacMode;
import com.pax.dal.entity.EPedType;
import com.pax.dal.entity.EUartPort;
import com.pax.dal.exceptions.PedDevException;
import com.pax.poslib.gl.convert.ConvertHelper;
import com.pax.poslib.neptune.Sdk;
import com.pax.poslib.ped.PedFactory;

/**
 * Utils for operate Ped
 */
public class PedHelper {
    private static final String TAG = "PedHelper";

    private PedHelper() {
        // do nothing
    }

    private static IDAL dal() {
        return Sdk.getInstance().getDal(BaseApplication.getAppContext());
    }

    public static IPed getPed() {
        IDAL idal = dal();
        if (idal == null) {
            throw new IllegalStateException("Neptune DAL is null — cannot open PED");
        }
        IPed ped;
        try {
            int pedMode = idal.getSys().getPedMode();
            if (pedMode == 2) {
                ped = PedFactory.getPedKeyIsolation(ParamHelper.getCurrentPed());
            } else {
                ped = PedFactory.getPed(ParamHelper.getCurrentPed());
            }
            if (ParamHelper.getCurrentPed() == EPedType.EXTERNAL_TYPEA
                    || ParamHelper.getCurrentPed() == EPedType.EXTERNAL_TYPEC) {
                ped.setPort(EUartPort.PINPAD);
            }
        } catch (Throwable t) {
            // UnsatisfiedLinkError / NoClassDefFoundError are Errors, not Exception.
            LogUtils.e(TAG, "getPedMode failed, falling back to dal.getPed", t);
            ped = idal.getPed(ParamHelper.getCurrentPed());
        }
        return ped;
    }

    /**
     * decrypt dataIn with ECB
     * @param dataIn encrypted data
     * @return decrypted data
     */
    public static byte[] calcDes(byte[] dataIn){
        IDAL idal = dal();
        if (idal == null){
            return new byte[0];
        }
        IPed ped = getPed();
        try {
            return ped.calcDes(Constants.INDEX_TDK_DECRYPT, null,dataIn, (byte) 0);
        } catch (PedDevException e) {
            LogUtils.e(e);
            return new byte[0];
        }
    }

    /**
     * 获取主秘钥索引
     *
     * @param index 0~99的主秘钥索引值
     * @return 1~100的主秘钥索引值
     */
    public static int getMainKeyIndex(int index) {
        return index + 1;
    }

    /**
     * write plainTex TDK
     * @param tdkValue tdkValue
     * @param tdkKcv tdkKcv
     * @throws PedDevException PedDevException
     */
    public static void writeTDKForDecrypt(byte[] tdkValue, byte[] tdkKcv) throws PedDevException {
        ECheckMode checkMode = ECheckMode.KCV_ENCRYPT_0;
        if (tdkKcv == null || tdkKcv.length == 0) {
            checkMode = ECheckMode.KCV_NONE;
        }
        IPed ped = getPed();
        ped.writeKey(EPedKeyType.TMK, (byte) 0,
                EPedKeyType.TDK, Constants.INDEX_TDK_DECRYPT, tdkValue, checkMode, tdkKcv);
    }

    public static byte[] calcMac(String data) throws PedDevException {
        IPed ped = getPed();
        return ped.getMac(Constants.INDEX_TAK, data.getBytes(), EPedMacMode.MODE_00);
    }

    public static byte[] getMac(byte[] data) throws PedDevException {
        String beforeCalcMacData = ConvertHelper.getConvert().bcdToStr(data);

        byte[] mac = calcMac(beforeCalcMacData);
        if (mac.length > 0) {
            return ConvertHelper.getConvert().bcdToStr(mac).substring(0, 8).getBytes();
        }
        return "".getBytes();
    }

    public static void writeTIK(int groupIndex,int srcKeyIndex,byte[] tikValue,byte[] ksn) throws PedDevException{
        IPed ped = getPed();
        ped.writeTIK((byte) groupIndex,(byte)srcKeyIndex,tikValue,ksn,ECheckMode.KCV_NONE,null);
    }
}
