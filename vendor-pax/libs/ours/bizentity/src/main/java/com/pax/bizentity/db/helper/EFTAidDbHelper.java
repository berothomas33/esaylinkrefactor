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
import com.pax.bizentity.db.dao.EFTAidBeanDao;
import com.pax.bizentity.entity.clss.eft.EFTAidBean;

/**
 * EFT database helper
 */
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

    /**
     * Find EFTAidBean by aid.
     *
     * @param aid AID
     * @return EFTAidBean
     */
    @Nullable
    public EFTAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(EFTAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
