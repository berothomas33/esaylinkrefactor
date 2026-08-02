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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pax.bizentity.entity.Issuer;

/**
 * Mag card helper
 */
public interface IMagCardService {
    /**
     * read mag data
     */
    void magRead();
    /**
     * Gets track1 data
     * @return track1 data
     */
    @Nullable
    String getTrack1();
    /**
     * Gets track2 data
     * @return track2 data
     */
    @Nullable
    String getTrack2();
    /**
     * Gets track3 data
     * @return track3 data
     */
    @Nullable
    String getTrack3();
    /**
     * Gets pan, the result is ciphertext in p2pe mode
     * @return pan
     */
    @Nullable
    String getPan();

    /**
     * Gets pan block
     * @return pan block
     */
    @NonNull
    String getPanBlock();

    /**
     * Gets masked pan
     * @param pattern masked pattern
     * @return masked pan
     */
    @Nullable
    String getMaskedPan(String pattern);

    /**
     * Gets expire Date
     * @return expire Date
     */
    @Nullable
    String getExpireDate();

    /**
     * Gets cardholder name
     * @return cardholder name
     */
    @Nullable
    String getCardholderName();

    /**
     * Gets Issuer by pan(use maskedPan in p2pe mode)
     * @return Issuer
     */
    @Nullable
    Issuer getMatchedIssuerByPan();

    /**
     * check is valid pan
     * @return result
     */
    boolean isValidPan();

    /**
     * Gets service code
     * @return service code
     */
    @Nullable
    String getServiceCode();
}
