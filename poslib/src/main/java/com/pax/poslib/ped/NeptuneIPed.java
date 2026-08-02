/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2021-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                           Author                      Action
 * 2022/02/11                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.poslib.ped;

import com.pax.commonlib.application.BaseApplication;
import com.pax.dal.IDAL;
import com.pax.dal.IPed;
import com.pax.dal.entity.EPedType;
import com.pax.dal.pedkeyisolation.IPedKeyIsolation;
import com.pax.poslib.neptune.Sdk;

/**
 * Internal PED Implementation
 */
class NeptuneIPed {
    private final IDAL dal = Sdk.getInstance().getDal(BaseApplication.getAppContext());

    private NeptuneIPed() {
    }

    private static class Holder {
        private static final NeptuneIPed INSTANCE = new NeptuneIPed();
    }

    public static NeptuneIPed getInstance() {
        return Holder.INSTANCE;
    }

    public IPed getPed(EPedType type) {
        return dal.getPed(type);
    }

    public IPedKeyIsolation getPedKeyIsolation(EPedType type) {
        return dal.getPedKeyIsolation(type);
    }
}
