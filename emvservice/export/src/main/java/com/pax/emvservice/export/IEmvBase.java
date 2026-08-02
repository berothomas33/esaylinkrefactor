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
 * 20210610 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.export;

import androidx.annotation.NonNull;
import com.pax.bizentity.entity.Issuer;

public interface IEmvBase {
    /**
     * Gets value for specific tag
     * @param tag emv tag
     * @return value
     */
    public byte[] getTlv(int tag);

    /**
     * Sets value for specific tag
     * @param tag emv tag
     * @param value value
     */
    void setTlv(int tag, byte[] value);
    /**
     * Gets pan, the result is ciphertext in p2pe mode
     * @return pan
     */
    @NonNull
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
    String getMaskedPan(String pattern);

    /**
     * Gets expire Date
     * @return expire Date
     */
    String getExpireDate();

    /**
     * Gets cardholder name
     * @return cardholder name
     */
    String getCardholderName();
    /**
     * Gets Issuer by pan(use maskedPan in p2pe mode)
     * @return Issuer
     */
    Issuer getMatchedIssuerByPan();

    /**
     * Gets track2 data
     * @return track2 data,the result is ciphertext in p2pe mode
     */
    String getTrack2Data();
}
