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

import com.pax.dal.IPed;
import com.pax.dal.entity.EPedType;
import com.pax.dal.pedkeyisolation.IPedKeyIsolation;

/**
 * IPed Factory
 */
public class PedFactory {
    private PedFactory() {}

    public static IPed getPed(EPedType type) {
        return NeptuneIPed.getInstance().getPed(type);
    }

    public static IPedKeyIsolation getPedKeyIsolation(EPedType type) {
        return NeptuneIPed.getInstance().getPedKeyIsolation(type);
    }
}
