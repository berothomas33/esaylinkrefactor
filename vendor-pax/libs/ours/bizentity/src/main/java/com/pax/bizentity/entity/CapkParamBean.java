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
