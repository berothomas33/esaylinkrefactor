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
    public static final String SUPPORT_SELECT_COMM_MODE = "SUPPORT_SELECT_COMM_MODE";
    public static final String COMM_MODE = "COMM_MODE";
    public static final String COMM_TYPE = "COMM_TYPE";
    public static final String COMM_TIMEOUT = "COMM_TIMEOUT";
    public static final String MOBILE_NEED_USER = "MOBILE_NEED_USER";
    public static final String MOBILE_USER = "MOBILE_USER";
    public static final String MOBILE_KEEP_ALIVE = "MOBILE_KEEP_ALIVE";
    public static final String MOBILE_TEL_NO = "MOBILE_TEL_NO";
    public static final String MOBILE_APN = "MOBILE_APN";
    public static final String MOBILE_PWD = "MOBILE_PWD";
    public static final String MOBILE_HOST_PORT = "MOBILE_HOST_PORT";
    public static final String MOBILE_HOST_PORT_BAK = "MOBILE_HOST_PORT_BAK";
    public static final String MOBILE_HOST_IP = "MOBILE_HOST_IP";
    public static final String MOBILE_HOST_IP_BAK = "MOBILE_HOST_IP_BAK";
    public static final String LAN_LOCAL_IP = "LAN_LOCAL_IP";
    public static final String LAN_NETMASK = "LAN_NETMASK";
    public static final String LAN_GATEWAY = "LAN_GATEWAY";
    public static final String LAN_DNS1 = "LAN_DNS1";
    public static final String LAN_DNS2 = "LAN_DNS2";
    public static final String LAN_HOST_IP = "LAN_HOST_IP";
    public static final String LAN_HOST_IP_BAK = "LAN_HOST_IP_BAK";
    public static final String LAN_HOST_PORT = "LAN_HOST_PORT";
    public static final String LAN_HOST_PORT_BAK = "LAN_HOST_PORT_BAK";
    public static final String LAN_DHCP = "LAN_DHCP";
    public static final String COMM_REDIAL_TIMES = "COMM_REDIAL_TIMES";
    
    public static final String EDC_SMTP_HOST = "EDC_SMTP_HOST";
    public static final String EDC_SMTP_PORT = "EDC_SMTP_PORT";
    public static final String EDC_SMTP_USERNAME = "EDC_SMTP_USERNAME";
    public static final String EDC_SMTP_PASSWORD = "EDC_SMTP_PASSWORD";
    public static final String EDC_SMTP_ENABLE_SSL = "EDC_SMTP_ENABLE_SSL";
    public static final String EDC_SMTP_SSL_PORT = "EDC_SMTP_SSL_PORT";
    public static final String EDC_SMTP_FROM = "EDC_SMTP_FROM";
    
    public static final String MK_INDEX = "MK_INDEX";
    public static final String KEY_ALGORITHM = "KEY_ALGORITHM";
    public static final String MK_INDEX_MANUAL = "MK_INDEX_MANUAL";
    public static final String MK_VALUE = "MK_VALUE";
    public static final String PK_VALUE = "PK_VALUE";
    public static final String AK_VALUE = "AK_VALUE";
    public static final String DK_VALUE = "DK_VALUE";

    public static final String SEC_SYS_PWD = "SEC_SYS_PWD_256";
    public static final String SEC_MERCHANT_PWD = "SEC_MERCHANT_PWD_256";
    public static final String SEC_TERMINAL_PWD = "SEC_TERMINAL_PWD_256";
    public static final String SEC_VOID_PWD = "SEC_VOID_PWD_256";
    public static final String SEC_REFUND_PWD = "SEC_REFUND_PWD_256";
    public static final String SEC_ADJUST_PWD = "SEC_ADJUST_PWD_256";
    public static final String SEC_SETTLE_PWD = "SEC_SETTLE_PWD_256";
    
    public static final String ACQ_NAME = "ACQ_NAME";
    public static final String EDC_REVERSAL_RETRY = "EDC_REVERSAL_RETRY";
    public static final String MAX_TRANS_COUNT = "MAX_TRANS_COUNT";
    public static final String OFFLINE_TC_UPLOAD_TIMES = "OFFLINE_TC_UPLOAD_TIMES";
    public static final String OFFLINE_TC_UPLOAD_NUM = "OFFLINE_TC_UPLOAD_NUM";
    public static final String QUICK_PASS_TRANS_PIN_FREE_AMOUNT = "QUICK_PASS_TRANS_PIN_FREE_AMOUNT";
    public static final String QUICK_PASS_TRANS_SIGN_FREE_AMOUNT = "QUICK_PASS_TRANS_SIGN_FREE_AMOUNT";
    public static final String EDC_MERCHANT_NAME_EN = "EDC_MERCHANT_NAME_EN";
    public static final String EDC_MERCHANT_ADDRESS = "EDC_MERCHANT_ADDRESS";
    public static final String EDC_CURRENCY_LIST = "EDC_CURRENCY_LIST";
    public static final String EDC_RECEIPT_NUM = "EDC_RECEIPT_NUM";
    public static final String EDC_TRACE_NO = "EDC_TRACE_NO";
    public static final String EDC_SUPPORT_TIP = "EDC_SUPPORT_TIP";
    public static final String EDC_SUPPORT_KEYIN = "EDC_SUPPORT_KEYIN";
    public static final String SUPPORT_USER_AGREEMENT = "SUPPORT_USER_AGREEMENT";
    public static final String EDC_ENABLE_PAPERLESS = "EDC_ENABLE_PAPERLESS";
    public static final String OTHTC_VERIFY = "OTHTC_VERIFY";
    public static final String QUICK_PASS_TRANS_PIN_FREE_SWITCH = "QUICK_PASS_TRANS_PIN_FREE_SWITCH";
    public static final String QUICK_PASS_TRANS_FLAG = "QUICK_PASS_TRANS_FLAG";
    public static final String QUICK_PASS_TRANS_SWITCH = "QUICK_PASS_TRANS_SWITCH";
    public static final String QUICK_PASS_TRANS_CDCVM_FLAG = "QUICK_PASS_TRANS_CDCVM_FLAG";
    public static final String QUICK_PASS_TRANS_SIGN_FREE_FLAG = "QUICK_PASS_TRANS_SIGN_FREE_FLAG";
    public static final String TTS_SALE = "TTS_SALE";
    public static final String TTS_VOID = "TTS_VOID";
    public static final String TTS_REFUND = "TTS_REFUND";
    public static final String TTS_PREAUTH = "TTS_PREAUTH";
    public static final String TTS_ADJUST = "TTS_ADJUST";

    public static final String LOG_FILE_INDEX = "LOG_FILE_INDEX";
    public static final String EDC_PED_MODE = "EDC_PED_MODE";
    public static final String EDC_CLSS_MODE = "EDC_CLSS_MODE";
    public static final String EDC_PRINTER_TYPE = "EDC_PRINTER_TYPE";
    public static final String EDC_ENABLE_SAVE_LOG = "EDC_ENABLE_SAVE_LOG";
    public static final String EDC_ENABLE_PRINT_LOG = "EDC_ENABLE_PRINT_LOG";
    public static final String RESULT_READER_TYPE = "RESULT_READER_TYPE";
    public static final String EDC_SOLVE_IMAG_CLS_CONFLICT = "EDC_SOLVE_IMAG_CLS_CONFLICT";
    public static final String EDC_PHYSICAL_CLS_LIGHT = "EDC_PHYSICAL_CLS_LIGHT";

    public static final String BATCH_UP_STATUS = "BATCH_UP_STATUS";
    public static final String IS_FIRST_RUN = "IS_FIRST_RUN";

    public static final String NEED_DOWNLOAD_PAXSTORE_PARAM = "NEED_DOWNLOAD_PAXSTORE_PARAM";
    public static final String IS_PAXSTORE_UPDATE_PARAM_EXCEPTION = "IS_PAXSTORE_UPDATE_PARAM_EXCEPTION";
    public static final String PAXSTORE_UPDATE_PARAM_EXCEPTION = "PAXSTORE_UPDATE_PARAM_EXCEPTION";

    public static final String USER_GUIDE_ENABLE = "USER_GUIDE_ENABLE";
    public static final String PSW_GUIDE_ENABLE = "PASSWORD_GUIDE_ENABLE";
    public static final String SETTINGS_GUIDE_ENABLE = "SETTINGS_GUIDE_ENABLE";
    public static final String SALE_GUIDE_ENABLE = "SALE_GUIDE_ENABLE";

    public static final String MERCHANT_CATEGORY_CODE = "MERCHANT_CATEGORY_CODE";
    public static final String TRANS_REFER_CURRENCY_CONVERSION = "TRANS_REFER_CURRENCY_CONVERSION";
    public static final String ENABLE_PRINT_RECEIPT = "ENABLE_PRINT_RECEIPT";

    public static final String AD_BANNER_URL_1 = "AD_BANNER_URL_1";
    public static final String AD_BANNER_URL_2 = "AD_BANNER_URL_2";
    public static final String AD_BANNER_URL_3 = "AD_BANNER_URL_3";

    public static final String AD_BANNER_IMG_1 = "AD_BANNER_IMG_1";
    public static final String AD_BANNER_IMG_2 = "AD_BANNER_IMG_2";
    public static final String AD_BANNER_IMG_3 = "AD_BANNER_IMG_3";

    private ConfigKeyConstant() {
        // do nothing
    }

    public enum BatchUpStatus {
        WORKED(0),//not in batch upload 不用批上送，已经完成了
        BATCH_UP(1);//in batch upload 需要批上送
        private final int status;

        BatchUpStatus(int status) {
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }


    public static final class CommSslType {
        /**
         * Conect to server via NO_SSL.
         */
        public static final String NO_SSL = "NO SSL";
        /**
         * Conect to server via SSL.
         */
        public static final String SSL = "SSL";

        private CommSslType() {
            // do nothing
        }
    }

    public static final class CommMode {
        /**
         * Use HTTPS and JSON communication mode
         */
        public static final String HTTPS_JSON = "HTTPS + JSON";
        /**
         * Use TCP and ISO8583 communication mode
         */
        public static final String TCP_ISO = "TCP + ISO8583";

        private CommMode() {
            // do nothing
        }
    }

    public static final class CommType {
        /**
         * LAN communication type.
         */
        public static final String LAN = "LAN";
        /**
         * MOBILE cellular communication type.
         */
        public static final String MOBILE = "MOBILE";
        /**
         * WIFI communication type.
         */
        public static final String WIFI = "WIFI";
        /**
         * DEMO communication type. When using this type of communication, transaction data will
         * not be uploaded to the server.
         */
        public static final String DEMO = "DEMO";

        private CommType() {
            // do nothing
        }
    }

    public static final class PrintType {
        /**
         * INTERNAL printer type.
         */
        public static final String INTERNAL = "Internal";
        /**
         * BP60A_PRINTER type.
         */
        public static final String BP60A_PRINTER = "BP60A_Printer";

        private PrintType() {
            // do nothing
        }
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

    public static final class ClssMode {
        /**
         * INTERNAL contactless mode.
         */
        public static final String INTERNAL = "Internal";
        /**
         * EXTERNAL contactless mode.
         */
        public static final String EXTERNAL = "External";
        /**
         * INTERNAL and EXTERNAL contactless mode.
         */
        public static final String INTERNAL_EXTERNAL = "Both Internal And External";

        private ClssMode() {
            // do nothing
        }
    }

}
