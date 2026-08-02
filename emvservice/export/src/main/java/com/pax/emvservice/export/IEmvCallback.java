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
 * 20210611 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.export;

import android.os.ConditionVariable;

public abstract class IEmvCallback {
    private final ConditionVariable cv;
    private final IEmvBase emvBase;

    public IEmvCallback(ConditionVariable cv,IEmvBase emvBase) {
        this.cv = cv;
        this.emvBase = emvBase;
    }
}
