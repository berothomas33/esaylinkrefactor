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

package com.pax.emvservice.export.contact;

public interface IContactResultListener {
    /**
     * need fallback
     */
    void fallback();

    /**
     * online denied
     */
    void onlineDenied();

    /**
     * offline denied
     * @param resultCode error code
     */
    void offlineDenied(int resultCode);

    /**
     * online card denied
     */
    void onlineCardDenied(int resultCode);

    /**
     * offline approved
     * @param needSignature needSignature
     */
    void offlineApproved(boolean needSignature, boolean needSetARC);

    /**
     * online approved
     * @param needSignature needSignature
     */
    void onlineApproved(boolean needSignature);

    /**
     * online failed
     */
    void onlineFailed();

    /**
     * simple flow
     */
    void simpleFlowEnd();
}
