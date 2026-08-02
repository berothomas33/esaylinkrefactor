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
 * 20210603 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.export;

import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.process.contact.IContactCallback;
import com.pax.emvservice.export.contact.IContactResultListener;

/**
 * service for emv contact
 */
public interface IEmvContactService extends IEmvBase{
    /**
     * user cancel during contact process
     * @param userCancel userCancel
     */
    void setUserCancel(boolean userCancel);
    /**
     * application timeout,and finish the emv process
     * @param isTimeOut isTimeOut
     */
    void timeOut(boolean isTimeOut);
    /**
     * check whether application is timeout
     */
    boolean isTimeOut();
    /**
     * check whether user cancel
     */
    boolean isUserCancel();

    /**
     * emv pretreatment
     * @param emvProcessParam emvProcessParam
     * @return pretreatment result
     */
    int preTransProcess(EmvProcessParam emvProcessParam);

    /**
     * start contact process,need handle timeout situation
     * @param contactCallback contactCallback
     * @return result
     */
    int startTransProcess(IContactCallback contactCallback);

    /**
     * check contact result
     * @param listener result callback
     */
    void checkContactResult(IContactResultListener listener);
}
