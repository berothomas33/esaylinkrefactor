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

package com.pax.emvlib.base.contact;

import com.pax.emvbase.process.EmvBase;
import com.pax.emvbase.process.contact.IContactCallback;
import com.pax.emvbase.process.entity.TransResult;

/**
 * File description
 *
 * @author yehongbo
 * @date 2022/4/15
 */
public abstract class BaseContactProcess extends EmvBase {
    public abstract void registerEmvProcessListener(IContactCallback emvTransProcessListener);

    /**
     * EMV application selection only. Must be called, and must succeed (resultCode ==
     * RetCode.EMV_OK, transResult still unset), before {@link #readApplicationData()}.
     */
    public abstract TransResult selectApplication();

    /**
     * Read application data (+ confirm-card callback). Must be called, and must succeed, after
     * {@link #selectApplication()} and before {@link #cardAuthentication()}.
     */
    public abstract TransResult readApplicationData();

    /**
     * CAPK lookup + card authentication (EMVCardAuth). Must be called, and must succeed with
     * transResult still unset, after {@link #readApplicationData()} and before
     * {@link #startTransProcess()}. A set transResult here (e.g. RESULT_SIMPLE_FLOW_END) means
     * the transaction is already finished — do not call startTransProcess().
     */
    public abstract TransResult cardAuthentication();
}
