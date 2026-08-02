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
 * 2021/06/16                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

import androidx.annotation.NonNull;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.param.common.Config;

/**
 * EFT Kernel Param
 */
public class EFTParam extends BaseParam<EFTAid> {
    // From AID
    private byte[] tacDefault;
    private byte[] tacDenial;
    private byte[] tacOnline;
    private byte[] termCapability;
    private byte[] floorLimitBytes;
    private byte[] transLimitBytes;
    private byte[] cvmLimitBytes;

    // From Config
    private byte referCurrExp;
    private byte termTypeForReader;
    private byte[] exTermCapability;

    // From EmvTransParam / Clss_TransParam
    private byte[] transNo;
    private byte transCurrExpForReader;
    private byte[] termId;

    // TLV param
    private byte[] kernelId;

    public EFTParam() {
        // You can set default values for some fields here
        termType = 0x22;
        transCurrExp = 0x02;
        kernelId = new byte[]{(byte) 0x81};
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Load from other parameter class ///////////////////////////////////////////////////

    @NonNull
    @Override
    public EFTParam loadFromAid(EFTAid aid) {
        appVersion = aid.getVersion();
        tacDefault = aid.getTacDefault();
        tacDenial = aid.getTacDenial();
        tacOnline = aid.getTacOnline();
        acquirerId = aid.getAcquirerId();
        floorLimitBytes = aid.getFloorLimitBytes();
        transLimitBytes = aid.getTransLimitBytes();
        cvmLimitBytes = aid.getCvmLimitBytes();
        termType = aid.getTermType();
        termTypeForReader = aid.getTermType();
        termCapability = aid.getTermCapability();
        exTermCapability = aid.getTermAddCapability();
        kernelId = aid.getKernelId();
        return this;
    }

    @NonNull
    @Override
    public EFTParam loadFromConfig(Config config) {
        merchantCategoryCode = config.getMerchantCategoryCode();
        merchantNameLocation = config.getMerchantNameAndLocationBytes();
        referCurrCon = config.getConversionRatio();
        referCurrCode = config.getTransReferenceCurrencyCode();
        referCurrExp = config.getTransReferenceCurrencyExponent();
        merchantId = config.getMerchantId();
        countryCode = config.getTerminalCountryCode();
        return this;
    }

    @NonNull
    @Override
    public EFTParam loadFromEmvTransParam(EmvTransParam param) {
        termId = param.getTerminalID();
        transType = param.getTransType();
        transCurrCode = param.getTransCurrencyCode();
        transCurrExp = param.getTransCurrencyExponent();
        transCurrExpForReader = param.getTransCurrencyExponent();
        transDate = param.getTransDate();
        transTime = param.getTransTime();
        amount = param.getAmountBytes();
        otherAmount = param.getAmountOtherBytes();
        transNo = param.getTransTraceNoBytes();
        return this;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Getter and Setter /////////////////////////////////////////////////////////////////

    /**
     * Get Terminal Action Code – Default.
     *
     * @return Terminal Action Code – Default
     * @see EFTAid#getTacDefault()
     */
    public byte[] getTacDefault() {
        return tacDefault;
    }

    /**
     * Get Terminal Action Code – Denial.
     *
     * @return Terminal Action Code – Denial
     * @see EFTAid#getTacDenial()
     */
    public byte[] getTacDenial() {
        return tacDenial;
    }

    /**
     * Get Terminal Action Code – Online.
     *
     * @return Terminal Action Code – Online
     * @see EFTAid#getTacOnline()
     */
    public byte[] getTacOnline() {
        return tacOnline;
    }

    /**
     * Get terminal capabilities bytes.
     *
     * @return Terminal capabilities bytes
     * @see EFTAid#getTermCapability()
     */
    public byte[] getTermCapability() {
        return termCapability;
    }

    /**
     * Get contactless offline limit bytes.
     *
     * @return Contactless offline limit bytes
     * @see EFTAid#getFloorLimitBytes()
     */
    public byte[] getFloorLimitBytes() {
        return floorLimitBytes;
    }

    /**
     * Get contactless transaction limit bytes.
     *
     * @return Contactless transaction limit bytes
     * @see EFTAid#getTransLimitBytes()
     */
    public byte[] getTransLimitBytes() {
        return transLimitBytes;
    }

    /**
     * Get contactless CVM limit bytes.
     *
     * @return Contactless CVM limit bytes
     * @see EFTAid#getCvmLimitBytes()
     */
    public byte[] getCvmLimitBytes() {
        return cvmLimitBytes;
    }

    /**
     * Get terminal ID bytes.
     *
     * @return Terminal ID bytes
     * @see EmvTransParam#getTerminalID()
     */
    public byte[] getTermId() {
        return termId;
    }

    /**
     * Get additional terminal capability bytes.
     *
     * @return Additional terminal capability bytes
     * @see EFTAid#getTermAddCapability()
     */
    public byte[] getExTermCapability() {
        return exTermCapability;
    }

    /**
     * Get transaction currency exponent.
     *
     * @return Transaction currency exponent
     * @see EmvTransParam#getTransCurrencyExponent()
     */
    public byte getTransCurrExpForReader() {
        return transCurrExpForReader;
    }

    /**
     * Get terminal transaction currency exponent.
     *
     * @return Terminal transaction currency exponent
     * @see Config#getTransReferenceCurrencyExponent()
     */
    public byte getReferCurrExp() {
        return referCurrExp;
    }

    /**
     * Get terminal type.
     *
     * @return Terminal type
     * @see EFTAid#getTermType()
     */
    public byte getTermTypeForReader() {
        return termTypeForReader;
    }

    /**
     * Get transaction trace number bytes.
     *
     * @return Transaction trace number bytes
     * @see EmvTransParam#getTransTraceNoBytes()
     */
    public byte[] getTransNo() {
        return transNo;
    }

    /**
     * Get kernel ID.
     *
     * @return Kernel ID
     * @see EFTAid#getKernelId()
     */
    public byte[] getKernelId() {
        return kernelId;
    }
}
