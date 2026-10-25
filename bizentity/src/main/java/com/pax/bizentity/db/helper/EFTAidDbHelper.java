package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.EFTAidBeanDao;
import com.pax.bizentity.entity.clss.eft.EFTAidBean;

public class EFTAidDbHelper extends BaseDaoHelper<EFTAidBean> {
    private static class LazyHolder {
        public static final EFTAidDbHelper INSTANCE = new EFTAidDbHelper(EFTAidBean.class);
    }

    public static EFTAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public EFTAidDbHelper(Class<EFTAidBean> entityClass) {
        super(entityClass);
    }

    @Nullable
    public EFTAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(EFTAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
