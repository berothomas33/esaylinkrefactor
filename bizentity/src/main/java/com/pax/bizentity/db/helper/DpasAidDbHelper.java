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
import com.pax.bizentity.db.dao.DpasAidBeanDao;
import com.pax.bizentity.entity.clss.dpas.DpasAidBean;

/**
 * Dpas database helper
 */
public class DpasAidDbHelper extends BaseDaoHelper<DpasAidBean> {
    private static class LazyHolder {
        public static final DpasAidDbHelper INSTANCE = new DpasAidDbHelper(DpasAidBean.class);
    }

    public static DpasAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public DpasAidDbHelper(Class<DpasAidBean> entityClass) {
        super(entityClass);
    }

    /**
     * Find DpasAidBean by aid.
     *
     * @param aid AID
     * @return DpasAidBean
     */
    @Nullable
    public DpasAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(DpasAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
