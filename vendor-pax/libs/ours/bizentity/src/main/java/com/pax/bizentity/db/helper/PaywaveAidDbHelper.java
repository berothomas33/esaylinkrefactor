package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.PaywaveAidBeanDao;
import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean;

public class PaywaveAidDbHelper extends BaseDaoHelper<PaywaveAidBean> {
    private static class LazyHolder {
        public static final PaywaveAidDbHelper INSTANCE = new PaywaveAidDbHelper(PaywaveAidBean.class);
    }

    public static PaywaveAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PaywaveAidDbHelper(Class<PaywaveAidBean> entityClass) {
        super(entityClass);
    }

    @Nullable
    public PaywaveAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(PaywaveAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
