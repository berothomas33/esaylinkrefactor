package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.pure.PureAidBean;

public class PureAidDbHelper extends BaseDaoHelper<PureAidBean> {
    private static class LazyHolder {
        public static final PureAidDbHelper INSTANCE = new PureAidDbHelper(PureAidBean.class);
    }

    public static PureAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PureAidDbHelper(Class<PureAidBean> entityClass) {
        super(entityClass);
    }

}
