package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.AmexDrlBeanDao;
import com.pax.bizentity.entity.clss.amex.AmexDrlBean;

public class AmexDrlDbHelper extends BaseDaoHelper<AmexDrlBean> {
    private static class LazyHolder {
        public static final AmexDrlDbHelper INSTANCE = new AmexDrlDbHelper(AmexDrlBean.class);
    }

    public static AmexDrlDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public AmexDrlDbHelper(Class<AmexDrlBean> entityClass) {
        super(entityClass);
    }

    @Nullable
    public AmexDrlBean findDRL(@Nullable String drl) {
        if (drl == null || drl.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(AmexDrlBeanDao.Properties.ProgramId.eq(drl)).unique();
    }
}
