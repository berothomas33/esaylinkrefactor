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
 * 20200917  	         xieYb                  Create
 * ===========================================================================================
 */
package com.pax.commonlib.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;
import com.pax.commonlib.application.BaseApplication;

public class DeviceUtils {
    private DeviceUtils() {
        // do nothing
    }

    /**
     * check whether isScreenOn
     * @param context applicationContext
     * @return isScreenOn
     */
    public static boolean isScreenOn(Context context){
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager.isInteractive();
    }


    public static void wakeupScreen(int timeout) {
        PowerManager pm = (PowerManager) BaseApplication.getAppContext().getSystemService(Context.POWER_SERVICE);
        @SuppressLint("InvalidWakeLockTag") final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");
        wl.acquire();
        BaseApplication.getAppContext().runOnUiThreadDelay(new Runnable() {
            @Override
            public void run() {
                wl.release();
            }
        }, 1000L * (timeout + 1));
    }
}
