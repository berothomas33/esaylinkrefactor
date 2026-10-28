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
 * 20210610 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.bizentity.entity;

import java.util.List;

public class CapkParamBean {
    List<EmvCapk> capkList;
    List<CapkRevokeBean> capkRevokeList;

    public List<EmvCapk> getCapkList() {
        return capkList;
    }

    public void setCapkList(List<EmvCapk> capkList) {
        this.capkList = capkList;
    }

    public List<CapkRevokeBean> getCapkRevokeList() {
        return capkRevokeList;
    }

    public void setCapkRevokeList(List<CapkRevokeBean> capkRevokeList) {
        this.capkRevokeList = capkRevokeList;
    }
}
