package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.eft.EFTAidBean;

public class EFTAidDbHelper extends BaseDaoHelper<EFTAidBean> {
    private static class LazyHolder {
        public static final EFTAidDbHelper INSTANCE = new EFTAidDbHelper(EFTAidBean.class);
    }

    public static EFTAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public EFTAidDbHelper(Class<EFTAidBean> entityClass) {
        super(entityClass);
    }

}
