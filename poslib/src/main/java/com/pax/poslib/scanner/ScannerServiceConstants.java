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
 * 2021/08/17                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.poslib.scanner;

/**
 * Scanner service constants.
 *
 * The scanning service is now placed in the poslib module, because in the future it may be
 * necessary to use the scanning function in places other than transactions, and both
 * scanning and decoding are related to NeptuneLite.
 */
public class ScannerServiceConstants {
    /**
     * Use camera preview and ScanCodec decode
     */
    public static final String CAMERA = "scanner_Camera_ScanCodec";

    /**
     * Use ScannerHW decode
     */
    public static final String SCANNERHW = "scanner_ScannerHW";

    private ScannerServiceConstants() {
        // do nothing
    }
}
