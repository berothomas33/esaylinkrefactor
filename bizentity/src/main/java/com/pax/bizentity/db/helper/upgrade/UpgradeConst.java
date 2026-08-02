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
 * 2021/12/16                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.bizentity.db.helper.upgrade;

/**
 * Table upgrade rules classes key
 */
public class UpgradeConst {
    public static final String UPGRADE_1_2 = "1_TO_2";
    public static final String UPGRADE_4_5 = "4_TO_5";
    public static final String UPGRADE_5_6 = "5_TO_6";
    public static final String UPGRADE_6_7 = "6_TO_7";

    private UpgradeConst() {
        // do nothing
    }

    public static String getKey(int oldVersion, int newVersion) {
        return oldVersion + "_TO_" + newVersion;
    }
}
