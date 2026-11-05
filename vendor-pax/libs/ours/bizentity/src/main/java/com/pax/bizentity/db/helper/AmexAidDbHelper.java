package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.amex.AmexAidBean;

public class AmexAidDbHelper extends BaseDaoHelper<AmexAidBean> {
    private static class LazyHolder {
        public static final AmexAidDbHelper INSTANCE = new AmexAidDbHelper(AmexAidBean.class);
    }

    public static AmexAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public AmexAidDbHelper(Class<AmexAidBean> entityClass) {
        super(entityClass);
    }

}
