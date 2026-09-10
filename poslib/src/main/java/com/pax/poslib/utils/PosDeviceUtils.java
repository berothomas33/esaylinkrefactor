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
 * 20210514 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.poslib.utils;

import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IDAL;
import com.pax.dal.entity.EBeepMode;
import com.pax.dal.entity.TrackData;
import com.pax.dal.exceptions.MagDevException;
import com.pax.poslib.neptune.Sdk;

public class PosDeviceUtils {
    private static IDAL idal = Sdk.getInstance().getDal(BaseApplication.getAppContext());
    /**
     * MAX key index
     */
    public static final byte INDEX_TAK = 0x01;
    /**
     * PIN key index
     */
    public static final byte INDEX_TPK = 0x03;
    /**
     * DES key index
     */
    public static final byte INDEX_TDK = 0x05;

    private PosDeviceUtils() {
        // do nothing
    }

    /**
     * beep error
     */
    public static void beepErr() {
        if (idal != null) {
            idal.getSys().beep(EBeepMode.FREQUENCE_LEVEL_6, 200);
        }
    }

    public static TrackData getTrackData(){
        try {
            return idal.getMag().read();
        } catch (MagDevException e) {
            LogUtils.e(e);
            return null;
        }
    }
}
