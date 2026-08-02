/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2019-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date	                Author	               Action
 * 20210508 	        xieYb                  Create
 * ===========================================================================================
 *
 */
package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.JcbAidBeanDao;
import com.pax.bizentity.entity.clss.jcb.JcbAidBean;

/**
 * Jcb database helper
 */
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

    /**
     * Find JcbAidBean by aid.
     *
     * @param aid AID
     * @return JcbAidBean
     */
    @Nullable
    public JcbAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(JcbAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
