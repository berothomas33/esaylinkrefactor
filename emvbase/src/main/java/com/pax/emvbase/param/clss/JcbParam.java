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
 * 2021/06/17                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

import androidx.annotation.NonNull;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.param.common.Config;

/**
 * JCB Kernel Param
 */
public class JcbParam extends BaseParam<JcbAid> {

    // From Aid
    private byte[] tacDefault;
    private byte[] tacDenial;
    private byte[] tacOnline;
    private byte[] cvmTransLimit;
    private byte[] floorLimitBytes;
    private byte[] transLimitBytes;
    private byte[] cvmLimitBytes;

    // TLV param
    private byte[] termInterchange;
    private byte[] termCompatFlag;
    private byte[] combinationOption;

    public JcbParam() {
        // You can set default values for some fields here
        acquirerId = null;
        merchantNameLocation = null;
        merchantCategoryCode = new byte[]{0x00, 0x00};
        termType = 0x22;
        transCurrExp = 0x02;
        termInterchange = new byte[]{(byte) 0xF3, (byte) 0x80, (byte) 0x00};
        termCompatFlag = new byte[]{0x03};
        combinationOption = new byte[]{(byte) 0x3B, 0x00};
    }

    @NonNull
    @Override
    public JcbParam loadFromAid(JcbAid aid) {
        tacDefault = aid.getTacDefault();
        tacDenial = aid.getTacDenial();
        tacOnline = aid.getTacOnline();
        acquirerId = aid.getAcquirerId();
        floorLimitBytes = aid.getFloorLimitBytes();
        transLimitBytes = aid.getTransLimitBytes();
        cvmTransLimit = aid.getCvmTransLimitBytes();
        cvmLimitBytes = aid.getCvmLimitBytes();
        termType = aid.getTermType();
        termInterchange = aid.getTermInterchange();
        termCompatFlag = aid.getTermCompatFlag();
        combinationOption = aid.getCombinationOption();
        return this;
    }

    @NonNull
    @Override
    public JcbParam loadFromConfig(Config config) {
        countryCode = config.getTerminalCountryCode();
        merchantNameLocation = config.getMerchantNameAndLocationBytes();
        merchantCategoryCode = config.getMerchantCategoryCode();
        return this;
    }

    @NonNull
    @Override
    public JcbParam loadFromEmvTransParam(EmvTransParam param) {
        transType = param.getTransType();
        transCurrCode = param.getTransCurrencyCode();
        transCurrExp = param.getTransCurrencyExponent();
        transDate = param.getTransDate();
        transTime = param.getTransTime();
        amount = param.getAmountBytes();
        otherAmount = param.getAmountOtherBytes();
        return this;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Getter and Setter /////////////////////////////////////////////////////////////////

    /**
     * Get Terminal Action Code – Default.
     *
     * @return Terminal Action Code – Default
     */
    public byte[] getTacDefault() {
        return tacDefault;
    }

    /**
     * Get Terminal Action Code – Denial.
     *
     * @return Terminal Action Code – Denial
     */
    public byte[] getTacDenial() {
        return tacDenial;
    }

    /**
     * Get Terminal Action Code – Online.
     *
     * @return Terminal Action Code – Online
     */
    public byte[] getTacOnline() {
        return tacOnline;
    }

    /**
     * Get terminal interchange profile.
     *
     * @return Terminal interchange profile
     * @see JcbAid#getTermInterchange()
     */
    public byte[] getTermInterchange() {
        return termInterchange;
    }

    /**
     * Get terminal compatibility indicator.
     *
     * @return Terminal compatibility indicator
     * @see JcbAid#getTermCompatFlag()
     */
    public byte[] getTermCompatFlag() {
        return termCompatFlag;
    }

    /**
     * Get combination option.
     *
     * @return Combination option
     * @see JcbAid#getCombinationOption()
     */
    public byte[] getCombinationOption() {
        return combinationOption;
    }

    /**
     * Get contactless transaction limit (On-device CVM) bytes.
     *
     * @return Contactless transaction limit (On-device CVM) bytes
     */
    public byte[] getCvmTransLimit() {
        return cvmTransLimit;
    }

    /**
     * Get contactless offline limit bytes.
     *
     * @return Contactless offline limit bytes
     */
    public byte[] getFloorLimitBytes() {
        return floorLimitBytes;
    }

    /**
     * Get contactless transaction limit (No On-device CVM) bytes.
     *
     * @return Contactless transaction limit (No On-device CVM) bytes
     */
    public byte[] getTransLimitBytes() {
        return transLimitBytes;
    }

    /**
     * Get contactless CVM limit bytes.
     *
     * @return Contactless CVM limit bytes
     */
    public byte[] getCvmLimitBytes() {
        return cvmLimitBytes;
    }
}
