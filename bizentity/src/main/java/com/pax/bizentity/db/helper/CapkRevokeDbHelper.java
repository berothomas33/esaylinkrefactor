package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.CapkRevokeBean;

public class CapkRevokeDbHelper extends BaseDaoHelper<CapkRevokeBean> {
    private static class LazyHolder {
        public static final CapkRevokeDbHelper INSTANCE = new CapkRevokeDbHelper(CapkRevokeBean.class);
    }

    public static CapkRevokeDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public CapkRevokeDbHelper(Class entityClass) {
        super(entityClass);
    }

}
