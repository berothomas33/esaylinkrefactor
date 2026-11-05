package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;

public class PaypassAidDbHelper extends BaseDaoHelper<PayPassAidBean> {
    private static class LazyHolder {
        public static final PaypassAidDbHelper INSTANCE = new PaypassAidDbHelper(PayPassAidBean.class);
    }

    public static PaypassAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PaypassAidDbHelper(Class<PayPassAidBean> entityClass) {
        super(entityClass);
    }

}
