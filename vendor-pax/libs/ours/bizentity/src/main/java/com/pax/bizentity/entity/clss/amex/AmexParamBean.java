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
