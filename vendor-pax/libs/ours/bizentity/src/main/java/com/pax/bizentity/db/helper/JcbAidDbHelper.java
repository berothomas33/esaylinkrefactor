package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.JcbAidBeanDao;
import com.pax.bizentity.entity.clss.jcb.JcbAidBean;

public class JcbAidDbHelper extends BaseDaoHelper<JcbAidBean> {
    private static class LazyHolder {
        public static final JcbAidDbHelper INSTANCE = new JcbAidDbHelper(JcbAidBean.class);
    }

    public static JcbAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public JcbAidDbHelper(Class<JcbAidBean> entityClass) {
        super(entityClass);
    }

    @Nullable
    public JcbAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(JcbAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
