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
 * 2022/03/28                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.emvenhance.emvflow.pin;

/**
 * PIN Task interface
 */
public interface IPinTask {

    /**
     * Start input PIN.
     *
     * @param panBlock PAN Block
     * @param isSupportPinByPass Whether support PIN Bypass or not
     * @return Return code
     */
    int start(String panBlock,
            boolean isSupportPinByPass);

    /**
     * PIN Callback.
     */
    interface PinCallback {
        /**
         * On PIN input.
         *
         * @param inputLen Current PIN length
         */
        void onInput(int inputLen);

        /**
         * PIN input finish.
         */
        void onFinish();

        /**
         * PIN input canceled.
         */
        void onCancel();

        /**
         * No PINPAD error.
         */
        void onNoPinPad();

        /**
         * PIN input timeout.
         */
        void onTimeout();

        /**
         * PED Error.
         *
         * @param reason Error reason
         */
        void onError(String reason);
    }
}
