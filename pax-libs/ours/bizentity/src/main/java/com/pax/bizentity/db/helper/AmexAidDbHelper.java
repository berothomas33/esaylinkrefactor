package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.AmexAidBeanDao;
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

    @Nullable
    public AmexAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(AmexAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
