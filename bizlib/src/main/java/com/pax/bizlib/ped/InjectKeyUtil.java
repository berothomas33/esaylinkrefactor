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
 * 2021/06/02                     YeHongbo                    Create
 * ===========================================================================================
 */
package com.pax.bizlib.ped;

import com.pax.bizlib.trans.Device;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.export.ConfigKeyConstant;
import com.pax.configservice.export.IConfigParamService;
import com.pax.configservice.impl.ConfigParamService;
import com.pax.poslib.gl.convert.ConvertHelper;

/**
 * The type Inject key util.
 */
public class InjectKeyUtil {
    private static final IConfigParamService configParamService = new ConfigParamService();

    private InjectKeyUtil() {
        // do nothing
    }

    /**
     * Inject mksk.
     *
     * @param type the type
     */
    public static void injectMKSK(String type) {
        if (!ConfigKeyConstant.CommType.DEMO.equals(type)) {
            return;
        }
        if (isKeyExist()) {
            return;
        }
        try {
            Device.writeTMK(configParamService.getInt(ConfigKeyConstant.MK_INDEX), ConvertHelper.getConvert().strToBcdPaddingLeft("54DCBF79AEB970329E97B98651E619CE"));
            Device.writeTPK(ConvertHelper.getConvert().strToBcdPaddingLeft("9D8D00D7DB16D3F8C1C475F192B22751"), null);
            Device.writeTAK(ConvertHelper.getConvert().strToBcdPaddingLeft("B5DDF952B2707E670102030405060708"), null);
            Device.writeTDK(ConvertHelper.getConvert().strToBcdPaddingLeft("768A5B4EFB48BC30C1CC3FD22522714D"), null);
            //for external device,TDK value is the result of decryption of TPK."07E6D931EA75734AA4E00483ADF2134F" is decrypted by "9D8D00D7DB16D3F8C1C475F192B22751"
            PedHelper.writeTDKForDecrypt(ConvertHelper.getConvert().strToBcdPaddingLeft("07E6D931EA75734AA4E00483ADF2134F"), null);
        } catch (Exception e) {
            LogUtils.e("InjectKeyUtil", e);
        }
    }


    /**
     * Is key exist boolean.
     *
     * @return the boolean
     */
    public static Boolean isKeyExist() {
        try {
            Device.calcMac("6222024301000244614");
            return true;
        } catch (Exception e) {
            LogUtils.e("InjectKeyUtil", e);
            return false;
        }
    }
}
