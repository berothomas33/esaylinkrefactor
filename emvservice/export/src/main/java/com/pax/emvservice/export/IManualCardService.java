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
 * 20210604 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.export;

/**
 * manual card helper
 */
public interface IManualCardService {
    /**
     * Gets pan, the result is ciphertext in p2pe mode
     * @param cardNo manual cardNo
     * @return pan
     */
    String getPan(String cardNo);

    /**
     * Gets masked pan
     * @param cardNo manual cardNo
     * @param pattern masked pattern
     * @return masked pan
     */
    String getMaskedPan(String cardNo, String pattern);
    /**
     * Gets pan block
     * @param cardNo  manual cardNo
     * @return pan block
     */
    String getPanBlock(String cardNo);
}
