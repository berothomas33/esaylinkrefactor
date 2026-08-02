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
 * 2021/08/12                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.commonlib.event.message;

/**
 * Application init message
 */
public class InitEvent extends Event {

    public enum Status {
        CHECK_NEPTUNE_FAILED,
        CHECK_PWD_DONE,
        CHECK_PWD_CANCEL,
        SHOW_CONFIG_DONE,
        GRANT_PERMISSION_DONE
    }

    public static InitEvent onCheckNeptuneFailed() {
        return new InitEvent(Status.CHECK_NEPTUNE_FAILED, null);
    }

    public static InitEvent onCheckPwdDone() {
        return new InitEvent(Status.CHECK_PWD_DONE, null);
    }

    public static InitEvent onCheckPwdCancel() {
        return new InitEvent(Status.CHECK_PWD_CANCEL, null);
    }

    public static InitEvent onShowConfigDone() {
        return new InitEvent(Status.SHOW_CONFIG_DONE, null);
    }

    public static InitEvent onGrantPermissionDone() {
        return new InitEvent(Status.GRANT_PERMISSION_DONE, null);
    }

    public InitEvent(Status status) {
        super(status);
    }

    public InitEvent(Status status, Object data) {
        super(status, data);
    }
}
