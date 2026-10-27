package com.pax.bizentity.db.helper;

import com.pax.bizentity.db.dao.IssuerDao;
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

    public final Issuer findIssuer(String issuerName) {
        if (issuerName == null || issuerName.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(IssuerDao.Properties.Name.eq(issuerName)).unique();
    }

    public final Issuer findIssuerByIssuerId(long issuerId) {
        return getNoSessionQuery().where(IssuerDao.Properties.Id.eq(issuerId)).unique();
    }

}
