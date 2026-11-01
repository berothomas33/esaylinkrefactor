package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.PBOCAidBeanDao;
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

    @Nullable
    public PBOCAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(PBOCAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
