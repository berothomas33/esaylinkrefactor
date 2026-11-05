package com.pax.bizentity.db.helper;

import com.pax.bizentity.entity.clss.jcb.JcbAidBean;

public class JcbAidDbHelper extends BaseDaoHelper<JcbAidBean> {
    private static class LazyHolder {
        public static final JcbAidDbHelper INSTANCE = new JcbAidDbHelper(JcbAidBean.class);
    }

    public static JcbAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public JcbAidDbHelper(Class<JcbAidBean> entityClass) {
        super(entityClass);
    }

}
