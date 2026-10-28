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
 * 20210614 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvbase.process.contact;

import com.pax.emvbase.process.entity.OnlineResultWrapper;
import java.util.List;

/**
 * callback during contact process
 */
public interface IContactCallback{
    /**
     *  When Select app
     * @param isFirstSelect isFirstSelect
     * @param candList CandidateAID list
     * @return select result
     */
    int onWaitAppSelect(boolean isFirstSelect, List<CandidateAID> candList);

    /**
     * When need to enter pin
     * @param isOnlinePin is online pin
     * @param supportPINByPass  supportPINByPass
     * @param leftTimes left retry Times
     * @param pinData pinData
     * @return enter pin result
     */
    int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes, byte[] pinData);

    /**
     * need show card confirm ui,include timeout condition,can use BaseContactProcess to get card info
     * @return result
     */
    int showConfirmCard();

    /**
     * need show online process
     * @return OnlineResultWrapper
     */
    OnlineResultWrapper startOnlineProcess();
}
