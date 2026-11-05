package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.mir.MirAidBean;

public class MirAidDbHelper extends BaseDaoHelper<MirAidBean> {
    private static class LazyHolder {
        public static final MirAidDbHelper INSTANCE = new MirAidDbHelper(MirAidBean.class);
    }

    public static MirAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public MirAidDbHelper(Class<MirAidBean> entityClass) {
        super(entityClass);
    }

}
