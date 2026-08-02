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
 * 20210615 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.export.pin;

import androidx.annotation.Nullable;
import com.pax.emvservice.export.exceptions.PinException;

public interface IPinService {
    /**
     * External Ped situation
     *  when PCI verify offline PIN interface is not used, this parameter is output.
     *  The application should return plaintext PIN in it with string ended with '\x00'.
     * @param encryptPinData encryptPinData
     * @param panBlock panBlock
     * @return plaintext PIN in it with string ended with '\x00'.
     */
    byte[] externalOfflinePinData(String encryptPinData, String panBlock);

    /**
     * Gets encrypted PinData
     * @param panBlock panBlock
     * @param supportBypass supportBypass
     * @param landscape landscape
     * @return encrypted PinData
     * @throws PinException PinException
     */
    byte[] getEncryptedPinData(String panBlock, boolean supportBypass, boolean landscape) throws PinException;

    /**
     * input pin callback(not pci mode)
     * @param pedInputPinListener pedInputPinListener
     */
    void setInputPinListener(@Nullable PinInputCallback.Callback pedInputPinListener);
}
