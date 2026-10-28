package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.PayPassAidBeanDao;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;

public class PaypassAidDbHelper extends BaseDaoHelper<PayPassAidBean> {
    private static class LazyHolder {
        public static final PaypassAidDbHelper INSTANCE = new PaypassAidDbHelper(PayPassAidBean.class);
    }

    public static PaypassAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PaypassAidDbHelper(Class<PayPassAidBean> entityClass) {
        super(entityClass);
    }

    @Nullable
    public PayPassAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(PayPassAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
