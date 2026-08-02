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

import android.widget.EditText;
import androidx.annotation.IntRange;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.commonlib.utils.ViewUtils;
import com.pax.dal.IDAL;
import com.pax.dal.IPicc;
import com.pax.dal.entity.EBeepMode;
import com.pax.dal.entity.ENavigationKey;
import com.pax.dal.entity.EPiccType;
import com.pax.dal.entity.TrackData;
import com.pax.dal.exceptions.MagDevException;
import com.pax.dal.exceptions.PiccDevException;
import com.pax.poslib.model.ModelInfo;
import com.pax.poslib.neptune.Sdk;

public class PosDeviceUtils {
    private static final String TAG = "DeviceUtils";
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
     * beep ok
     */
    public static void beepOk() {
        idal.getSys().beep(EBeepMode.FREQUENCE_LEVEL_3, 100);
        idal.getSys().beep(EBeepMode.FREQUENCE_LEVEL_4, 100);
        idal.getSys().beep(EBeepMode.FREQUENCE_LEVEL_5, 100);
    }

    /**
     * beep error
     */
    public static void beepErr() {
        if (idal != null) {
            idal.getSys().beep(EBeepMode.FREQUENCE_LEVEL_6, 200);
        }
    }

    /**
     * beep prompt
     */
    public static void beepPrompt() {
        idal.getSys().beep(EBeepMode.FREQUENCE_LEVEL_6, 50);
    }
    /**
     * enable/disable status bar
     *
     * @param enable true/false
     */
    public static void enableStatusBar(boolean enable) {
        idal.getSys().enableStatusBar(enable);
    }

    /**
     * enable/disable home and recent key
     *
     * @param enable true/false
     */
    public static void enableHomeRecentKey(boolean enable) {
        idal.getSys().enableNavigationKey(ENavigationKey.HOME, enable);
        idal.getSys().enableNavigationKey(ENavigationKey.RECENT, enable);
    }
    /**
     * Sets picc led.
     *
     * @param index  the index
     * @param status the status
     */
    public static void setPiccLed(final @IntRange(from = -1, to = 3) int index, int status) {
        final IPicc picc = idal.getPicc(EPiccType.INTERNAL);
        try {
            if (index >= 0 && status > 0) {
                picc.setLed((byte) (1 << (3 - index)));
            } else {
                picc.setLed((byte) 0);
            }
        } catch (PiccDevException e) {
            LogUtils.e(TAG, "", e);
        }
    }

    /**
     * Sets picc led with exception.
     *
     * @param index  the index
     * @param status the status
     * @throws PiccDevException the picc dev exception
     */
    public static void setPiccLedWithException(final @IntRange(from = -1, to = 3) int index, int status) throws PiccDevException {
        final IPicc picc = idal.getPicc(EPiccType.INTERNAL);
        if (index >= 0 && status > 0) {
            picc.setLed((byte) (1 << (3 - index)));
        } else {
            picc.setLed((byte) 0);
        }
    }

    /**
     * disable system keyboard when EditText focused on Device with Physical keyboard
     *
     * @param et EditText
     */
    public static void configInput(EditText et) {
        IDAL dal = Sdk.getInstance().getDal(BaseApplication.getAppContext());
        if (!ModelInfo.getInstance().isSupportKeyboard()){
            return;
        }
        boolean enableKeyEvent = dal.getSys().enableKeyEvent();
        if (!enableKeyEvent){
            return;
        }
        ViewUtils.showInput(et,false);
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
