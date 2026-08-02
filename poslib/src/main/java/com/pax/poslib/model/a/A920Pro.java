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
 * 2021/08/17                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.poslib.model.a;

import com.pax.poslib.model.DefaultModel;

/**
 * A920Pro Device Information.
 */
public class A920Pro extends DefaultModel {
    @Override
    public boolean isSupportMag() {
        return true;
    }

    @Override
    public boolean isSupportIcc() {
        return true;
    }

    @Override
    public boolean isSupportPicc() {
        return true;
    }

    @Override
    public boolean isSupportCamera() {
        return true;
    }

    @Override
    public boolean isSupportScannerHw() {
        return true;
    }

    @Override
    public boolean isSupportScanButton() {
        return true;
    }

    @Override
    public boolean isSupportPrinter() {
        return true;
    }
}
