package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean;

public class PaywaveAidDbHelper extends BaseDaoHelper<PaywaveAidBean> {
    private static class LazyHolder {
        public static final PaywaveAidDbHelper INSTANCE = new PaywaveAidDbHelper(PaywaveAidBean.class);
    }

    public static PaywaveAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PaywaveAidDbHelper(Class<PaywaveAidBean> entityClass) {
        super(entityClass);
    }

}
