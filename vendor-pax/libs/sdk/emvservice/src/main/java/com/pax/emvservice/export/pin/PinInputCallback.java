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
 * 20210709 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.export.pin;

public interface PinInputCallback {
    interface Callback{
        void keyEvent(EKeyCode key);
    }
    interface PCICallback extends Callback{
    }
    interface NormalCallback extends Callback{
    }
    public enum EKeyCode {
        KEY_0,
        KEY_1,
        KEY_2,
        KEY_3,
        KEY_4,
        KEY_5,
        KEY_6,
        KEY_7,
        KEY_8,
        KEY_9,
        KEY_ENTER,
        KEY_CANCEL,
        KEY_CLEAR,
        KEY_FUNC,
        KEY_ALPH,
        KEY_STAR,
        NO_KEY;

        private EKeyCode() {
        }
    }
}
