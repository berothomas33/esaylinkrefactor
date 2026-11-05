package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.Issuer;

public class IssuerDbHelper extends BaseDaoHelper<Issuer> {
    private static class LazyHolder {
        public static final IssuerDbHelper INSTANCE = new IssuerDbHelper(Issuer.class);
    }

    public static IssuerDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public IssuerDbHelper(Class<Issuer> entityClass) {
        super(entityClass);
    }

}
