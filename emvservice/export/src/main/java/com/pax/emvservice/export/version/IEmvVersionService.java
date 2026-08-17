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
package com.pax.emvservice.export.version;

/**
 * service for fetch emv libs version
 */
public interface IEmvVersionService {
    /**
     * Query the version number of Emv Kernel
     * @return version number
     */
    String getEmvVersion();
    /**
     * Query the version number of Entry Kernel.
     * @return version number
     */
    String getEntryVersion();
    /**
     * Query the version number of PayPass kernel.
     * @return version number
     */
    String getPayPassVersion();
    /**
     * Query the version number of PayWave kernel.
     * @return version number
     */
    String getPayWaveVersion();
    /**
     * Query the version number of Rupay kernel.
     * @return version number
     */
    String getRupayVersion();
    /**
     * Query the version number of PBOC kernel.
     * @return version number
     */
    String getPbocVersion();
    /**
     * Query the version number of Jcb kernel.
     * @return version number
     */
    String getJcbVersion();

    /**
     * Query the version number of Amex kernel.
     * @return version number
     */
    String getAmexVersion();
    /**
     * Query the version number of Dpas kernel.
     * @return version number
     */
    String getDpasVersion();
    /**
     * Query the version number of Mir kernel.
     * @return version number
     */
    String getMIRVersion();
    /**
     * Query the version number of Pure kernel.
     * @return version number
     */
    String getPureVersion();
    /**
     * Query the version number of EFT kernel.
     * @return version number
     */
    String getEFTVersion();
}
