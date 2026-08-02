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

/**
 * service for fetch card information
 */
public interface IEmvCardInfoService {
    /**
     * Gets Track 1 Data
     * @return Track 1 Data
     */
    String getTrack1();

    /**
     * Gets Track 2 Data
     * @return Track 2 Data
     */
    String getTrack2();

    /**
     * Gets Track 3 Data
     * @return Track 3 Data
     */
    String getTrack3();

    /**
     * Gets Application Primary Account Number (PAN),tag 0x5A
     * @return Application Primary Account Number (PAN)
     */
    String getPan();

    /**
     * Gets masked Application Primary Account Number (PAN),tag 0x5A
     * @return masked Application Primary Account Number (PAN)
     */
    String getMaskedPan();

    /**
     * Gets Application Primary Account Number (PAN) Sequence Number (PSN),tag 0x5F34
     * @return Application Primary Account Number (PAN) Sequence Number (PSN)
     */
    String getCardSequence();

    /**
     * Gets Application Expiration Date,tag 0x5F24
     * @return Application Expiration Date
     */
    String getExpireDate();

    /**
     * Gets Cardholder Name
     * @return Cardholder Name,tag 5F20
     */
    String getCardHolderName();

    /**
     * Gets serviceCode
     * @return serviceCode
     */
    String getServiceCode();
}
