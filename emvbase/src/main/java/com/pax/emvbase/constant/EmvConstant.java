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
 * 20210608 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvbase.constant;

public class EmvConstant {
    private EmvConstant() {
        // do nothing
    }

    public static final class ContactCallbackStatus {
        /**
         * The constant CONTACT_OK == RetCode.EMV_OK,<=0 Success or The sequence number selected by the user (For example: 0 stands for List[0] was selected).
         */
        public static final int CONTACT_OK = 0;
        /**
         * The constant VERITY_FAILED ,Verify failed. or The application offers nothing for the tag
         */
        public static final int VERITY_FAILED = -1;
        /**
         * The constant USER_CANCEL == RetCode.EMV_USER_CANCEL,Canceled by user
         */
        public static final int USER_CANCEL = -7;
        /**
         * The constant TIMEOUT == RetCode.EMV_TIME_OUT,Application selection timeout
         */
        public static final int TIMEOUT = -8;
        /**
         * The constant NO_PINPAD == RetCode.EMV_NO_PINPAD,There is no pin pad or pin pad does not work.
         */
        public static final int NO_PINPAD = -13;
        /**
         * The constant NO_PASSWORD == RetCode.EMV_NO_PASSWORD,No PIN entered or cardholder doesn鈥檛 want to input PIN
         */
        public static final int NO_PASSWORD = -14;
        /**
         * The constant DATA_ERR == RetCode.EMV_DATA_ERR
         */
        public static final int DATA_ERR = -9;
        /**
         * The constant DENIAL == RetCode.EMV_DENIAL
         */
        public static final int DENIAL = -11;

        private ContactCallbackStatus() {
            // do nothing
        }
    }

    public static final class EEnterPinType {
        /**
         * The constant 联机pin
         */
        public static final String ONLINE_PIN = "ONLINE_PIN";
        /**
         * The constant 脱机明文pin
         */
        public static final String OFFLINE_PLAIN_PIN = "OFFLINE_PLAIN_PIN";
        /**
         * The constant 脱机密文pin
         */
        public static final String OFFLINE_CIPHER_PIN = "OFFLINE_CIPHER_PIN";
        /**
         * The constant JEMV PCI MODE, no callback for offline pin
         */
        public static final String OFFLINE_PCI_MODE = "OFFLINE_PCI_MODE";

        private EEnterPinType() {
            // do nothing
        }
    }

    public static final class KernType {
        public static final int KERNTYPE_DEF = 0;
        public static final int KERNTYPE_JCB = 1;
        public static final int KERNTYPE_MC = 2;
        public static final int KERNTYPE_VIS = 3;
        public static final int KERNTYPE_PBOC = 4;
        public static final int KERNTYPE_AE = 5;
        public static final int KERNTYPE_ZIP = 6;
        public static final int KERNTYPE_FLASH = 7;
        public static final int KERNTYPE_EFT = 8;
        public static final int KERNTYPE_PURE = 9;
        public static final int KERNTYPE_PAGO = 10;
        public static final int KERNTYPE_MIR = 11;
        public static final int KERNTYPE_RUPAY = 12;
        public static final int KERNTYPE_WISE = 13;
        public static final int KERNTYPE_JUSTOUCH = 14;
        public static final int KERNTYPE_RFU = 255;

        private KernType() {
            // do nothing
        }
    }

}
