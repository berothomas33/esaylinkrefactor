package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.dpas.DpasAidBean;

public class DpasAidDbHelper extends BaseDaoHelper<DpasAidBean> {
    private static class LazyHolder {
        public static final DpasAidDbHelper INSTANCE = new DpasAidDbHelper(DpasAidBean.class);
    }

    public static DpasAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public DpasAidDbHelper(Class<DpasAidBean> entityClass) {
        super(entityClass);
    }

}
