package com.pax.bizentity.db.helper;

import com.pax.bizentity.db.dao.AcquirerDao;
import com.pax.bizentity.entity.Acquirer;

public class AcquirerDbHelper extends BaseDaoHelper<Acquirer> {
    private static class LazyHolder {
        public static final AcquirerDbHelper INSTANCE = new AcquirerDbHelper(Acquirer.class);
    }

    public static AcquirerDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public AcquirerDbHelper(Class<Acquirer> entityClass) {
        super(entityClass);
    }

    public final Acquirer findAcquirer(String acquirerName) {
        return (Acquirer) this.getNoSessionQuery()
                .where(AcquirerDao.Properties.Name.eq(acquirerName))
                .unique();
    }

}
