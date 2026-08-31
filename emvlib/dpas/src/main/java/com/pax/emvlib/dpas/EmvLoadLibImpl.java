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
 * 2022/04/15                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvlib.dpas;

import com.pax.emvlib.base.IEmvLoadLibCallback;

/**
 * File description
 *
 * @author yehongbo
 * @date 2022/4/15
 */
public class EmvLoadLibImpl implements IEmvLoadLibCallback {
    @Override
    public void load() {
        //load contact
        System.loadLibrary("F_EMV_LIBC_PayDroid");
        System.loadLibrary("F_EMV_LIB_PayDroid");
        System.loadLibrary("JNI_EMV_v105");

        //load dpas
        System.loadLibrary("F_DPAS_LIB_PayDroid");
        System.loadLibrary("JNI_DPAS_v100");
    }
}
