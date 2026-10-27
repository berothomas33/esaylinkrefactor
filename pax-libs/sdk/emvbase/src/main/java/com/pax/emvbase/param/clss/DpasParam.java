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
 * DPAS Kernel Param
 */
public class DpasParam extends BaseParam<DpasAid> {

    // AID
    private byte[] tacDefault;
    private byte[] tacDenial;
    private byte[] tacOnline;
    private byte[] termCapability;
    private byte[] ttq;

    // API param
    // Clss_TransProc_DPAS(exceptFileFlag)
    private byte exceptFileFlag;

    public DpasParam() {
        // You can set default values for some fields here
        termType = 0x22;
        termCapability = new byte[]{(byte) 0xE0, (byte) 0xE1, (byte) 0xC8};
        merchantCategoryCode = new byte[]{0x00, 0x01};
        merchantNameLocation = new byte[]{0x00};
        acquirerId = new byte[]{0x00, 0x00, 0x00, 0x12, 0x34, 0x56};
        transCurrExp = 0x02;
        exceptFileFlag = 0x00;
        ttq = new byte[]{(byte) 0xB6, (byte) 0xC0, 0x00, (byte) 0xFF};
    }

    @Override
    @NonNull
    public DpasParam loadFromAid(DpasAid aid) {
        acquirerId = aid.getAcquirerId();
        appVersion = aid.getVersion();
        tacDefault = aid.getTacDefault();
        tacDenial = aid.getTacDenial();
        tacOnline = aid.getTacOnline();
        termType = aid.getTermType();
        termCapability = aid.getTermCapability();
        exceptFileFlag = aid.getExceptFileFlag();
        ttq = aid.getTtq();
        return this;
    }

    @Override
    @NonNull
    public DpasParam loadFromConfig(Config config) {
        merchantCategoryCode = config.getMerchantCategoryCode();
        merchantNameLocation = config.getMerchantNameAndLocationBytes();
        countryCode = config.getTerminalCountryCode();
        return this;
    }

    @Override
    @NonNull
    public DpasParam loadFromEmvTransParam(EmvTransParam param) {
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
     * @see DpasAid#getTacDefault()
     */
    public byte[] getTacDefault() {
        return tacDefault;
    }

    /**
     * Get Terminal Action Code – Denial.
     *
     * @return Terminal Action Code – Denial
     * @see DpasAid#getTacDenial()
     */
    public byte[] getTacDenial() {
        return tacDenial;
    }

    /**
     * Get Terminal Action Code – Online.
     *
     * @return Terminal Action Code – Online
     * @see DpasAid#getTacOnline()
     */
    public byte[] getTacOnline() {
        return tacOnline;
    }

    /**
     * Get terminal capabilities bytes.
     *
     * @return Terminal capabilities bytes
     * @see DpasAid#getTermCapability()
     */
    public byte[] getTermCapability() {
        return termCapability;
    }

    /**
     * Get PAN found in exception file list or not
     *
     * @return Whether the PAN is found in the exception file list or not
     * @see DpasAid#getExceptFileFlag()
     */
    public byte getExceptFileFlag() {
        return exceptFileFlag;
    }

    /**
     * Get terminal transaction qualifiers
     *
     * @return Terminal transaction qualifiers
     * @see DpasAid#getTtq()
     */
    public byte[] getTtq() {
        return ttq;
    }
}
