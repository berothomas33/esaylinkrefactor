/*
 *  * ===========================================================================================
 *  * = COPYRIGHT
 *  *          PAX Computer Technology(Shenzhen); CO., LTD PROPRIETARY INFORMATION
 *  *   This software is supplied under the terms of a license agreement or nondisclosure
 *  *   agreement with PAX Computer Technology(Shenzhen); CO., LTD and may not be copied or
 *  *   disclosed except in accordance with the terms in that agreement.
 *  *     Copyright (C); 2019-? PAX Computer Technology(Shenzhen); CO., LTD All rights reserved.
 *  * Description: // Detail description about the voidction of this module,
 *  *             // interfaces with the other modules, and dependencies.
 *  * Revision History:
 *  * Date                  Author	                 Action
 *  * 20200713  	         xieYb                   Modify
 *  * ===========================================================================================
 *
 */
package com.pax.configservice.export;

public class ConfigServiceConstant {
    //region service key
    public static final String CONFIGSERVICE_CONFIG = "configService_config";
    public static final String CONFIGSERVICE_ACQ_ISSUER = "configService_acquirer_issuer";
    public static final String CONFIGSERVICE_EMVPARAM = "configService_emvparam";
    //endregion
    public static final String INIT_CONFIG = "init_config";
    public static final String INIT_EMV = "init_emv";

    private ConfigServiceConstant() {
        // do nothing
    }
}
