package com.pax.bizentity.db.helper;

import com.pax.bizentity.db.dao.EmvAidDao;
import com.pax.bizentity.entity.EmvAid;

public class EmvAidDbHelper extends BaseDaoHelper<EmvAid> {
    private static class LazyHolder {
        public static final EmvAidDbHelper INSTANCE = new EmvAidDbHelper(EmvAid.class);
    }

    public static EmvAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public EmvAidDbHelper(Class<EmvAid> entityClass) {
        super(entityClass);
    }

    public final EmvAid findAID(String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return (EmvAid) getNoSessionQuery().where(EmvAidDao.Properties.Aid.eq(aid)).unique();
    }

}
