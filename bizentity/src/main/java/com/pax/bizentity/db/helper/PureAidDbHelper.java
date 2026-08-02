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
import com.pax.bizentity.db.dao.PureAidBeanDao;
import com.pax.bizentity.entity.clss.pure.PureAidBean;

/**
 * Jcb database helper
 */
public class PureAidDbHelper extends BaseDaoHelper<PureAidBean> {
    private static class LazyHolder {
        public static final PureAidDbHelper INSTANCE = new PureAidDbHelper(PureAidBean.class);
    }

    public static PureAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PureAidDbHelper(Class<PureAidBean> entityClass) {
        super(entityClass);
    }

    /**
     * Find PureAidBean by aid.
     *
     * @param aid AID
     * @return PureAidBean
     */
    @Nullable
    public PureAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(PureAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
