package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.DpasAidBeanDao;
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

    @Nullable
    public DpasAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(DpasAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
