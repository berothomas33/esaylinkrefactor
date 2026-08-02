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
 * Date                  Author	                 Action
 * 20190108  	         Steven.W                Create
 * ===========================================================================================
 */
package com.pax.bizlib.ped;

/**
 * constants
 */
public class Constants {
    private Constants() {
        //do nothing
    }
    /**
     * MAX key index
     */
    public static final byte INDEX_TAK = 0x01;
    /**
     * PIN key index
     */
    public static final byte INDEX_TPK = 0x03;
    /**
     * DES key index
     */
    public static final byte INDEX_TDK = 0x05;
    /**
     * DES key index for decrypt
     */
    public static final byte INDEX_TDK_DECRYPT = 0x07;
}
