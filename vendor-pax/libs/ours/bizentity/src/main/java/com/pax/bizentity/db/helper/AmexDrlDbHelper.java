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
import com.pax.bizentity.db.dao.AmexDrlBeanDao;
import com.pax.bizentity.entity.clss.amex.AmexDrlBean;

/**
 * amex drl database helper
 */
public class AmexDrlDbHelper extends BaseDaoHelper<AmexDrlBean> {
    private static class LazyHolder {
        public static final AmexDrlDbHelper INSTANCE = new AmexDrlDbHelper(AmexDrlBean.class);
    }

    public static AmexDrlDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public AmexDrlDbHelper(Class<AmexDrlBean> entityClass) {
        super(entityClass);
    }

    @Nullable
    public AmexDrlBean findDRL(@Nullable String drl) {
        if (drl == null || drl.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(AmexDrlBeanDao.Properties.ProgramId.eq(drl)).unique();
    }
}
