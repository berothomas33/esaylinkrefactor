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
import com.pax.bizentity.db.dao.PaywaveAidBeanDao;
import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean;

/**
 * paywave database helper
 */
public class PaywaveAidDbHelper extends BaseDaoHelper<PaywaveAidBean> {
    private static class LazyHolder {
        public static final PaywaveAidDbHelper INSTANCE = new PaywaveAidDbHelper(PaywaveAidBean.class);
    }

    public static PaywaveAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PaywaveAidDbHelper(Class<PaywaveAidBean> entityClass) {
        super(entityClass);
    }

    /**
     * Find PaywaveAidBean by aid.
     *
     * @param aid AID
     * @return PaywaveAidBean
     */
    @Nullable
    public PaywaveAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(PaywaveAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
