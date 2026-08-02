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
import com.pax.bizentity.db.dao.RupayAidBeanDao;
import com.pax.bizentity.entity.clss.rupay.RupayAidBean;

/**
 * Jcb database helper
 */
public class RupayAidDbHelper extends BaseDaoHelper<RupayAidBean> {
    private static class LazyHolder {
        public static final RupayAidDbHelper INSTANCE = new RupayAidDbHelper(RupayAidBean.class);
    }

    public static RupayAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public RupayAidDbHelper(Class<RupayAidBean> entityClass) {
        super(entityClass);
    }

    /**
     * Find RupayAidBean by aid.
     *
     * @param aid AID
     * @return RupayAidBean
     */
    @Nullable
    public RupayAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(RupayAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
