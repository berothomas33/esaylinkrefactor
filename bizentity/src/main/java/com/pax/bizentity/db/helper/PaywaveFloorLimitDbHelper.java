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

import com.pax.bizentity.entity.clss.paywave.PayWaveInterFloorLimitBean;

/**
 * paywave database helper
 */
public class PaywaveFloorLimitDbHelper extends BaseDaoHelper<PayWaveInterFloorLimitBean> {
    private static class LazyHolder {
        public static final PaywaveFloorLimitDbHelper INSTANCE = new PaywaveFloorLimitDbHelper(PayWaveInterFloorLimitBean.class);
    }

    public static PaywaveFloorLimitDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public PaywaveFloorLimitDbHelper(Class<PayWaveInterFloorLimitBean> entityClass) {
        super(entityClass);
    }
}
