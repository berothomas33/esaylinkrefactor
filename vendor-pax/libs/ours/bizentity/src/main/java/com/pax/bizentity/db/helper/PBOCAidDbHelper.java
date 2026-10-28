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
import com.pax.bizentity.db.dao.PBOCAidBeanDao;
import com.pax.bizentity.entity.clss.pboc.PBOCAidBean;

/**
 * Jcb database helper
 */
public class PBOCAidDbHelper extends BaseDaoHelper<PBOCAidBean> {
    private static class LazyHolder {
        public static final PBOCAidDbHelper INSTANCE = new PBOCAidDbHelper(PBOCAidBean.class);
    }

    public static PBOCAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PBOCAidDbHelper(Class<PBOCAidBean> entityClass) {
        super(entityClass);
    }

    /**
     * Find PBOCAidBean by aid.
     *
     * @param aid AID
     * @return PBOCAidBean
     */
    @Nullable
    public PBOCAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(PBOCAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
