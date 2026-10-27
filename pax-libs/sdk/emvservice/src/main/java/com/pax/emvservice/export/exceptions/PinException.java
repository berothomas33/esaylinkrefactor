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
 * 20210510 	        xieYb                  Create
 * ===========================================================================================
 *
 */
package com.pax.emvservice.export.exceptions;

public class PinException extends Exception{
    private static final long serialVersionUID = 1L;
    private static final String MODULE = "PIN";
    private String errModule = "";
    private String errCode;
    private String errMsg = "";
    public PinException(String errCode, String errMsg) {
        this(MODULE, errCode, errMsg);
    }
    public PinException(String module, String errCode, String errMsg) {
        super(module + "#" + errCode + "(" + errMsg + ")");
        this.errModule = module;
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    public PinException(String module, String errCode, String errMsg, String extraInfo) {
        super(module + "#" + errCode + "(" + errMsg + ")[" + extraInfo + "]");
        this.errModule = module;
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    public PinException(String module, String errCode, String errMsg, Throwable throwable) {
        super(module + "#" + errCode + "(" + errMsg + ")", throwable);
        this.errModule = module;
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    public PinException(String module, String errCode, String errMsg, String extraInfo, Throwable throwable) {
        super(module + "#" + errCode + "(" + errMsg + ")[" + extraInfo + "]", throwable);
        this.errModule = module;
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    public String getErrModule() {
        return this.errModule;
    }

    public String getErrCode() {
        return this.errCode;
    }

    public String getErrMsg() {
        return this.errMsg;
    }
}
