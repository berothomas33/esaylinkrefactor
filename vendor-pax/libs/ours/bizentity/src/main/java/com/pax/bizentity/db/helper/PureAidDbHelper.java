package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.PureAidBeanDao;
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

    @Nullable
    public PureAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(PureAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
