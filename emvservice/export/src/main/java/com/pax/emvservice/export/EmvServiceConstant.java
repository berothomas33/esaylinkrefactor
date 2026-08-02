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
 * 20210602 	        xieYb                  Create
 * ===========================================================================================
 *
 */
package com.pax.emvservice.export;

public class EmvServiceConstant {
    //region service key
    public static final String EMVSERVICE_PARAM = "emvService_param";
    public static final String EMVSERVICE_CONTACT = "emvService_contact";
    public static final String EMVSERVICE_CONTACTLESS = "emvService_contactLess";
    public static final String EMVSERVICE_EMV_VERSION = "emvService_emvVersion";
    public static final String EMVSERVICE_MANUAL_CARD = "emvService_manualCard";
    public static final String EMVSERVICE_MAG_CARD = "emvService_magCard";
    public static final String EMVSERVICE_PIN = "emvService_pin";
    public static final String EMVSERVICE_EMV_RSP = "emvService_emvResponse";
    public static final String EMVSERVICE_EMV_STATUS_CHECK = "emvService_status_check";
    //endregion

    private EmvServiceConstant() {
        // do nothing
    }
}
