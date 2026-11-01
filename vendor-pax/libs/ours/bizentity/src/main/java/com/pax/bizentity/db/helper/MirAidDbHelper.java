package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.MirAidBeanDao;
import com.pax.bizentity.entity.clss.mir.MirAidBean;

public class MirAidDbHelper extends BaseDaoHelper<MirAidBean> {
    private static class LazyHolder {
        public static final MirAidDbHelper INSTANCE = new MirAidDbHelper(MirAidBean.class);
    }

    public static MirAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public MirAidDbHelper(Class<MirAidBean> entityClass) {
        super(entityClass);
    }

    @Nullable
    public MirAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(MirAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
