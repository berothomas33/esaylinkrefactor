package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.EmvCapk;

public class EmvCapkDbHelper extends BaseDaoHelper<EmvCapk> {
    private static class LazyHolder {
        public static final EmvCapkDbHelper INSTANCE = new EmvCapkDbHelper(EmvCapk.class);
    }

    public static EmvCapkDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public EmvCapkDbHelper(Class<EmvCapk> entityClass) {
        super(entityClass);
    }

}
