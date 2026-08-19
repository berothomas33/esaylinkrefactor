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

package com.pax.emvservice.export.contact;
import com.pax.emvservice.export.api.IEmvBase;

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
     * EMV application selection (app select) only. Resets per-transaction state, registers
     * {@code contactCallback} for the whole transaction, and runs app select. Must be called,
     * and must return RetCode.EMV_OK, before {@link #startTransProcess(IContactCallback)}.
     * On any other result the transaction is finished — the caller should invoke
     * {@link #checkContactResult(IContactResultListener)} and not call startTransProcess.
     * @param contactCallback contactCallback
     * @return emv l2 lib api return code (RetCode.EMV_OK on success)
     */
    int selectApplication(IContactCallback contactCallback);

    /**
     * continue contact process after a successful {@link #selectApplication(IContactCallback)}
     * call — read app data through card auth / online processing. Need handle timeout situation.
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
