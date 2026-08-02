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
 * 2022/02/17                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.commonlib.utils;

import android.content.Intent;
import com.pax.commonlib.application.ActivityStack;
import com.pax.commonlib.application.BaseApplication;

/**
 * Restart Utils
 */
public class RestartUtils {

    private RestartUtils() {}

    /**
     * Restart the application.
     */
    public static void restart() {
        ActivityStack.getInstance().popAll();
        final Intent intent = BaseApplication.getAppContext().getPackageManager()
                .getLaunchIntentForPackage(BaseApplication.getAppContext().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        BaseApplication.getAppContext().startActivity(intent);

        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
