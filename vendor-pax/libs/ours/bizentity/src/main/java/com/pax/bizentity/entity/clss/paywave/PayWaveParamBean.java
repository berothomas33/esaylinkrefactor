package com.pax.bizentity.entity.clss.paywave;

import java.util.List;

public class PayWaveParamBean {
    private List<PaywaveAidBean> aid;

    public List<PaywaveAidBean> getAid() {
        return aid;
    }

    public void setAid(List<PaywaveAidBean> aid) {
        this.aid = aid;
    }

    @Override
    public String toString() {
        return "PayWaveParamBean{"
                + "aid=" + aid
                + '}';
    }
}
