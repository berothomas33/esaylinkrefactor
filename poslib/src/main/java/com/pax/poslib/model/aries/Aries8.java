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
 * 2021/08/30                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.poslib.model.aries;

import com.pax.poslib.model.DefaultModel;

/**
 * ARIES 8 Device Information.
 */
public class Aries8 extends DefaultModel {
    @Override
    public boolean isMagIccConflict() {
        return true;
    }

    @Override
    public int getCameraId() {
        return 1;
    }
}
