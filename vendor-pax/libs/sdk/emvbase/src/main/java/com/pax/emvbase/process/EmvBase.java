/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2020-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                  Author	                 Action
 * 20200518  	         JackHuang               Create
 * ===========================================================================================
 */
package com.pax.emvbase.process;

import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;

public abstract class EmvBase {
    /**
     * Emv process:
     *      1.core init
     *      2.add  aid
     * Clss process:
     *      1.entry init
     *      2.add aid
     */
    public abstract int preTransProcess(EmvProcessParam emvParam);


    /**
     * Process as below:
     *      1.detected card
     *      2.select application
     *      3.application initialization
     *      4.read application data
     *      5.offline data authentication
     *      6.terminal risk management
     *      7.ardholder authentication
     *      8.terminal behavior analysis
     *      9.First Generate AC
     */
    public abstract TransResult startTransProcess();


    /**
     * Process as below:
     *      1.Issuer Authentication
     *      2.Script Processing
     *      3.Complete Trans
     */
    public abstract TransResult completeTransProcess(IssuerRspData issuerRspData);

    /**
     * get tag value from emv kernel.
     * @param tag emv tag
     * @return tag value
     */
    public abstract byte[] getTlv(int tag);

    /**
     * Sets value on specific tag
     * @param tag emv tag
     * @param value tag value
     */
    public abstract void setTlv(int tag, byte[] value);
}
