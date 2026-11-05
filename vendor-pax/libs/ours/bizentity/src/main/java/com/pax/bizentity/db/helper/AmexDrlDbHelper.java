package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.amex.AmexDrlBean;

public class AmexDrlDbHelper extends BaseDaoHelper<AmexDrlBean> {
    private static class LazyHolder {
        public static final AmexDrlDbHelper INSTANCE = new AmexDrlDbHelper(AmexDrlBean.class);
    }

    public static AmexDrlDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public AmexDrlDbHelper(Class<AmexDrlBean> entityClass) {
        super(entityClass);
    }

}
