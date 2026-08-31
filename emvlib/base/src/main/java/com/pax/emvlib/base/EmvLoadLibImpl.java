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

package com.pax.emvlib.base;

/**
 * Load DEVICE、PUBLIC、ENTRY Library.
 */
public class EmvLoadLibImpl implements IEmvLoadLibCallback {
    @Override
    public void load() {
        //load device
        System.loadLibrary("F_DEVICE_LIB_PayDroid");

        //load public
        System.loadLibrary("F_PUBLIC_LIB_PayDroid");

        //load entry
        System.loadLibrary("F_ENTRY_LIB_PayDroid");
        System.loadLibrary("JNI_ENTRY_v105");
    }
}
