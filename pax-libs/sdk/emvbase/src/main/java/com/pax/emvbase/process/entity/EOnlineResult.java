/*
 *  ===========================================================================================
 *  = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *     This software is supplied under the terms of a license agreement or nondisclosure
 *     agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *     disclosed except in accordance with the terms in that agreement.
 *          Copyright (C) 2020 -? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 *  Description: // Detail description about the function of this module,
 *               // interfaces with the other modules, and dependencies.
 *  Revision History:
 *  Date	               Author	                   Action
 *  2020/06/04 	         Qinny Zhou           	      Create
 *  ===========================================================================================
 */
package com.pax.emvbase.process.entity;

import com.pax.jemv.clcommon.OnlineResult;

public enum EOnlineResult {
    APPROVE((byte) OnlineResult.ONLINE_APPROVE, 0),
    FAILED((byte) OnlineResult.ONLINE_FAILED, 1),
    REFER((byte) OnlineResult.ONLINE_REFER, 2),
    DENIAL((byte) OnlineResult.ONLINE_DENIAL, 3),
    ABORT((byte) OnlineResult.ONLINE_ABORT, 4);

    private final byte emvOnlineResult;
    private final int resultCode;

    EOnlineResult(byte emvOnlineResult, int resultCode) {
        this.emvOnlineResult = emvOnlineResult;
        this.resultCode = resultCode;
    }

    public byte getEmvOnlineResult() {
        return emvOnlineResult;
    }

    public int getResultCode() {
        return resultCode;
    }
}