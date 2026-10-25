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
    }

    public static final class PedMode {
        public static final String INTERNAL = "Internal";
        public static final String EXTERNAL_TYPE_A = "ExternalTypeA";
        public static final String EXTERNAL_TYPE_B = "ExternalTypeB";
        public static final String EXTERNAL_TYPE_C = "ExternalTypeC";

        private PedMode() {
        }
    }

}
