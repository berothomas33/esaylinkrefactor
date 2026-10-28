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
import com.pax.bizentity.db.dao.AmexAidBeanDao;
import com.pax.bizentity.entity.clss.amex.AmexAidBean;

/**
 * amex database helper
 */
public class AmexAidDbHelper extends BaseDaoHelper<AmexAidBean> {
    private static class LazyHolder {
        public static final AmexAidDbHelper INSTANCE = new AmexAidDbHelper(AmexAidBean.class);
    }

    public static AmexAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public AmexAidDbHelper(Class<AmexAidBean> entityClass) {
        super(entityClass);
    }

    /**
     * Find AmexAidBean by aid.
     *
     * @param aid AID
     * @return AmexAidBean
     */
    @Nullable
    public AmexAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(AmexAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
