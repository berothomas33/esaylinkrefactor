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
 * 2022/04/15                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvlib.base;

/**
 * EMV Library Loading Callback.
 *
 * Because the library versions that each submodule needs to load are different, the .so files
 * that need to be loaded may also be different.
 *
 * Each submodule creates an implementation class of this interface, and then registers it
 * through WMRouter. Eventually emvlib will traverse all the implementation classes of this
 * interface and execute their {@code load} method.
 */
public interface IEmvLoadLibCallback {
    /**
     * Load EMV Library
     */
    void load();
}
