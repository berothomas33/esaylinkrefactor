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
 * 20210507 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.configservice.export;

public class ConfigKeyConstant {
    public static final String MK_INDEX = "MK_INDEX";
    public static final String PK_VALUE = "PK_VALUE";
    public static final String AK_VALUE = "AK_VALUE";
    public static final String DK_VALUE = "DK_VALUE";

    public static final String ACQ_NAME = "ACQ_NAME";
    public static final String EDC_MERCHANT_NAME_EN = "EDC_MERCHANT_NAME_EN";
    public static final String EDC_MERCHANT_ADDRESS = "EDC_MERCHANT_ADDRESS";
    public static final String EDC_CURRENCY_LIST = "EDC_CURRENCY_LIST";
    public static final String MERCHANT_CATEGORY_CODE = "MERCHANT_CATEGORY_CODE";
    public static final String TRANS_REFER_CURRENCY_CONVERSION = "TRANS_REFER_CURRENCY_CONVERSION";

    public static final String EDC_PED_MODE = "EDC_PED_MODE";
    public static final String EDC_CLSS_MODE = "EDC_CLSS_MODE";
    public static final String RESULT_READER_TYPE = "RESULT_READER_TYPE";

    private ConfigKeyConstant() {
        // do nothing
    }

    public static final class PedMode {
        /**
         * INTERNAL PED mode.
         */
        public static final String INTERNAL = "Internal";
        /**
         * EXTERNAL_TYPE_A PED mode.
         */
        public static final String EXTERNAL_TYPE_A = "ExternalTypeA";
        /**
         * EXTERNAL_TYPE_B PED mode.
         */
        public static final String EXTERNAL_TYPE_B = "ExternalTypeB";
        /**
         * EXTERNAL_TYPE_C PED mode.
         */
        public static final String EXTERNAL_TYPE_C = "ExternalTypeC";

        private PedMode() {
            // do nothing
        }
    }

}
