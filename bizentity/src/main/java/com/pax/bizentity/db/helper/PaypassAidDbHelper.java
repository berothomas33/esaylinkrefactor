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
import com.pax.bizentity.db.dao.PayPassAidBeanDao;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;

/**
 * paypass database helper
 */
public class PaypassAidDbHelper extends BaseDaoHelper<PayPassAidBean> {
    private static class LazyHolder {
        public static final PaypassAidDbHelper INSTANCE = new PaypassAidDbHelper(PayPassAidBean.class);
    }

    public static PaypassAidDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PaypassAidDbHelper(Class<PayPassAidBean> entityClass) {
        super(entityClass);
    }

    /**
     * Find PayPassAidBean by aid.
     *
     * @param aid AID
     * @return PayPassAidBean
     */
    @Nullable
    public PayPassAidBean findAID(@Nullable String aid) {
        if (aid == null || aid.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(PayPassAidBeanDao.Properties.Aid.eq(aid)).unique();
    }
}
