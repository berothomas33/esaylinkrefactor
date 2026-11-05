package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.rupay.RupayAidBean;

public class RupayAidDbHelper extends BaseDaoHelper<RupayAidBean> {
    private static class LazyHolder {
        public static final RupayAidDbHelper INSTANCE = new RupayAidDbHelper(RupayAidBean.class);
    }

    public static RupayAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public RupayAidDbHelper(Class<RupayAidBean> entityClass) {
        super(entityClass);
    }

}
