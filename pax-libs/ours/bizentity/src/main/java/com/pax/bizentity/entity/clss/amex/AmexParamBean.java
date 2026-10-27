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
 * 20210707 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.bizentity.entity.clss.amex;

import java.util.List;

public class AmexParamBean {
   private List<AmexAidBean> aid;
   private List<AmexDrlBean> programID;

    public List<AmexAidBean> getAid() {
        return aid;
    }

    public void setAid(List<AmexAidBean> aid) {
        this.aid = aid;
    }

    public List<AmexDrlBean> getProgramID() {
        return programID;
    }

    public void setProgramID(List<AmexDrlBean> programID) {
        this.programID = programID;
    }
}
