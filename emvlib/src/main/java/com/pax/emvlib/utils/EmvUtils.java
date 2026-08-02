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
 * 20210624 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvlib.utils;

import com.pax.commonlib.utils.LogUtils;
import com.pax.emvlib.base.IEmvLoadLibCallback;
import com.sankuai.waimai.router.Router;
import java.util.List;

public class EmvUtils {
    private static final String TAG = "EmvUtils";

    private EmvUtils() {
        // do nothing
    }

    public static void loadLibrary(){
        try {
            List<IEmvLoadLibCallback> callbackList = Router.getAllServices(IEmvLoadLibCallback.class);
            if (callbackList != null && !callbackList.isEmpty()) {
                for (IEmvLoadLibCallback callback : callbackList) {
                    callback.load();
                }
            } else {
                LogUtils.e(TAG, "No Load Library Callback Implementation");
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "Load EMV Library Failed", e);
        }

        //load paypass
        System.loadLibrary("F_MC_LIB_PayDroid");
        System.loadLibrary("JNI_MC_v100_01");

        //load paywave
        System.loadLibrary("F_WAVE_LIB_PayDroid");
        System.loadLibrary("JNI_WAVE_v101");

        //load amex
        System.loadLibrary("F_AE_LIB_PayDroid");
        System.loadLibrary("JNI_AE_v101");

        //load jcb
        System.loadLibrary("F_JCB_LIB_PayDroid");
        System.loadLibrary("JNI_JCB_v100");

        //load mir
        System.loadLibrary("F_MIR_LIB_PayDroid");
        System.loadLibrary("JNI_MIR_v100");

        //load pboc
        System.loadLibrary("F_QPBOC_LIB_PayDroid");
        System.loadLibrary("JNI_QPBOC_v100");

        //load pure
        System.loadLibrary("F_PURE_LIB_PayDroid");
        System.loadLibrary("JNI_PURE_v100");

        //load rupay
        System.loadLibrary("F_RUPAY_LIB_PayDroid");
        System.loadLibrary("JNI_RUPAY_v100");

        //load eft
        System.loadLibrary("F_EFT_LIB_PayDroid");
        System.loadLibrary("JNI_EFT_v101_D1");
    }
}
