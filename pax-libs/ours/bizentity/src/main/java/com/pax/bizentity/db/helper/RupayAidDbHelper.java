package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.RupayAidBeanDao;
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

    @Nullable
    public RupayAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(RupayAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
