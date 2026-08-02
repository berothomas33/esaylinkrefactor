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
import com.pax.emvbase.process.contactless.IContactlessCallback;
import com.pax.emvservice.export.contactless.IContactlessResultListener;

/**
 * service for emv contactless
 */
public interface IEmvContactlessService extends IEmvBase{
    /**
     * emv contactless pretreatment
     * @param emvProcessParam emvProcessParam
     * @return pretreatment result
     */
    int preTransProcess(EmvProcessParam emvProcessParam);

    /**
     * start contactless process,need handle timeout situation
     * @param contactlessCallback contactlessCallback
     * @return result
     */
    int startTransProcess(IContactlessCallback contactlessCallback);

    /**
     * Gets kernel type
     * @return kernel type
     */
    int getKernelType();

    /**
     * check contactless result
     * @param clsResultListener clsResultListener
     */
    void checkClsResult(IContactlessResultListener clsResultListener);
    /**
     * Get capability from EmvParam
     * @return capability
     */
    byte[] getCapability();

    /**
     * Return if the last trans returned "Please See Phone" warning
     * @return boolean value
     */
    boolean getIsLastNeedSeePhone();

    /**
     * Sets isLastNeedSeePhone
     * @param isLastNeedSeePhone isLastNeedSeePhone
     */
    void setIsLastNeedSeePhone(boolean isLastNeedSeePhone);
}
