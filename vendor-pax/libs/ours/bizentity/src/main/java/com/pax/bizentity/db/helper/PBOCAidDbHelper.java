package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.pboc.PBOCAidBean;

public class PBOCAidDbHelper extends BaseDaoHelper<PBOCAidBean> {
    private static class LazyHolder {
        public static final PBOCAidDbHelper INSTANCE = new PBOCAidDbHelper(PBOCAidBean.class);
    }

    public static PBOCAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PBOCAidDbHelper(Class<PBOCAidBean> entityClass) {
        super(entityClass);
    }

}
