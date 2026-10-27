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
 * 2021/06/21                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

import androidx.annotation.NonNull;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.param.common.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Amex Kernel Param
 */
public class AmexParam extends BaseParam<AmexAid> {

    private final List<ExPayDRL> amexDRLs = new ArrayList<>();

    // From Aid
    private byte[] tacDefault;
    private byte[] tacDenial;
    private byte[] tacOnline;
    private byte[] termCapability;
    private byte[] unpredictableNumberRange;
    private byte supportOptTrans;
    private byte[] termTransCap;
    private byte delayAuthSupport;
    private byte[] dDOL;
    private byte[] tDOL;
    private long clssFloorLimit;
    private byte clssFloorLimitCheck;
    private byte exPayRdCap;
    private byte[] exFunction;
    private byte[] aucRFU;
    private byte supportFullOnline;
    private byte[] exTermCapability;
    private byte referCurrExp;

    // From EmvTransParam / Clss_TransParam
    private byte[] termId;

    public AmexParam() {
        // You can set default values for some fields here
        exFunction = new byte[]{1};
        unpredictableNumberRange = new byte[]{0x00, 0x60};
        supportOptTrans = 1;    // true: 1, false: 0
        aucRFU = new byte[27];
        supportFullOnline = 0;  // support: 1, do not support: 0
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Load from other parameter class ///////////////////////////////////////////////////

    @NonNull
    @Override
    public AmexParam loadFromAid(AmexAid aid) {
        appVersion = aid.getVersion();
        acquirerId = aid.getAcquirerId();
        unpredictableNumberRange = aid.getUnRange();
        supportOptTrans = aid.getSupportOptTrans();
        termTransCap = aid.getTermTransCap();
        delayAuthSupport = aid.getDelayAuthSupport();
        dDOL = aid.getdDOL();
        tDOL = aid.gettDOL();
        tacDefault = aid.getTacDefault();
        tacDenial = aid.getTacDenial();
        tacOnline = aid.getTacOnline();
        clssFloorLimit = aid.getFloorLimit();
        clssFloorLimitCheck = aid.getFloorLimitFlag();
        exPayRdCap = aid.getExPayRdCap();
        termType = aid.getTermType();
        termCapability = aid.getTermCapability();
        exTermCapability = aid.getTermAddCapability();
        exFunction = aid.getExFunction();
        supportFullOnline = aid.getSupportFullOnline();
        aucRFU = aid.getAucRFU();
        return this;
    }

    @NonNull
    @Override
    public AmexParam loadFromConfig(Config config) {
        countryCode = config.getTerminalCountryCode();
        merchantNameLocation = config.getMerchantNameAndLocationBytes();
        merchantCategoryCode = config.getMerchantCategoryCode();
        merchantId = config.getMerchantId();
        referCurrCon = config.getConversionRatio();
        referCurrCode = config.getTransReferenceCurrencyCode();
        referCurrExp = config.getTransReferenceCurrencyExponent();
        return this;
    }

    @NonNull
    @Override
    public AmexParam loadFromEmvTransParam(EmvTransParam param) {
        termId = param.getTerminalID();
        transCurrCode = param.getTransCurrencyCode();
        transCurrExp = param.getTransCurrencyExponent();
        return this;
    }


    //////////////////////////////////////////////////////////////////////////////////////
    // Getter ////////////////////////////////////////////////////////////////////////////

    /**
     * Get Terminal Action Code – Default.
     *
     * @return Terminal Action Code – Default
     * @see AmexAid#getTacDefault()
     */
    public byte[] getTacDefault() {
        return tacDefault;
    }

    /**
     * Get Terminal Action Code – Denial.
     *
     * @return Terminal Action Code – Denial
     * @see AmexAid#getTacDenial()
     */
    public byte[] getTacDenial() {
        return tacDenial;
    }

    /**
     * Get Terminal Action Code – Online.
     *
     * @return Terminal Action Code – Online
     * @see AmexAid#getTacOnline()
     */
    public byte[] getTacOnline() {
        return tacOnline;
    }

    /**
     * Get terminal capabilities bytes.
     *
     * @see AmexAid#getTermCapability()
     */
    public byte[] getTermCapability() {
        return termCapability;
    }

    /**
     * Get extend function parameter.
     *
     * @return Extend function parameter
     * @see AmexAid#getExFunction()
     */
    public byte[] getExFunction() {
        return exFunction;
    }

    /**
     * Get additional terminal capability.
     *
     * @return Additional terminal capability
     * @see AmexAid#getTermAddCapability()
     */
    public byte[] getExTermCapability() {
        return exTermCapability;
    }

    /**
     * Get terminal ID bytes
     *
     * @return Terminal ID bytes
     * @see EmvTransParam#getTerminalID()
     */
    public byte[] getTermId() {
        return termId;
    }

    /**
     * Get unpredictable number range.
     *
     * @return Unpredictable number range
     * @see AmexAid#getUnRange()
     */
    public byte[] getUnpredictableNumberRange() {
        return unpredictableNumberRange;
    }

    /**
     * Get support the optimization mode transaction flag.
     *
     * @return Support the optimization mode transaction flag
     * @see AmexAid#getSupportOptTrans()
     */
    public byte getSupportOptTrans() {
        return supportOptTrans;
    }

    /**
     * Get transaction reference currency exponent.
     *
     * @return Transaction reference currency exponent
     * @see Config#getTransReferenceCurrencyExponent()
     */
    public byte getReferCurrExp() {
        return referCurrExp;
    }

    /**
     * Get terminal transaction capabilities.
     *
     * @return Terminal transaction capabilities
     * @see AmexAid#getTermTransCap()
     */
    public byte[] getTermTransCap() {
        return termTransCap;
    }

    /**
     * Get delayed authorization support flag.
     *
     * @return Delay authorization support flag
     * @see AmexAid#getDelayAuthSupport()
     */
    public byte getDelayAuthSupport() {
        return delayAuthSupport;
    }

    /**
     * Get the third parameter of the {@code Clss_AddReaderParam_AE} class constructor.
     *
     * @return Third parameter of the {@code Clss_AddReaderParam_AE} class constructor
     * @see AmexAid#getAucRFU()
     */
    public byte[] getAucRFU() {
        return aucRFU;
    }

    /**
     * Get discretionary data object list.
     *
     * @return Discretionary data object list
     * @see AmexAid#getdDOL()
     */
    public byte[] getdDOL() {
        return dDOL;
    }

    /**
     * Get reader contactless floor limit.
     *
     * @return Contactless floor limit
     * @see AmexAid#getFloorLimit()
     */
    public long getClssFloorLimit() {
        return clssFloorLimit;
    }

    /**
     * Get transaction certificate data object list.
     *
     * @return Transaction certificate data object list
     * @see AmexAid#gettDOL()
     */
    public byte[] gettDOL() {
        return tDOL;
    }

    /**
     * Get reader contactless floor limit check or not.
     *
     * @return Reader contactless floor limit check or not
     * @see AmexAid#getFloorLimitFlag()
     */
    public byte getClssFloorLimitCheck() {
        return clssFloorLimitCheck;
    }

    /**
     * Get contactless reader capabilities.
     *
     * @return Contactless reader capabilities
     * @see AmexAid#getExPayRdCap()
     */
    public byte getExPayRdCap() {
        return exPayRdCap;
    }

    /**
     * Get support full online flag.
     *
     * @return Support full online flag
     * @see AmexAid#getSupportFullOnline()
     */
    public byte getSupportFullOnline() {
        return supportFullOnline;
    }

    /**
     * Get AMEX DRL list.
     *
     * @return DRL list
     */
    public List<ExPayDRL> getAmexDRLs() {
        return amexDRLs;
    }

    /**
     * Add AMEX DRL.
     *
     * @param drl DRL
     */
    public void addDrl(ExPayDRL drl) {
        if (contains(drl) == null) {
            amexDRLs.add(drl);
        }
    }

    /**
     * Determine whether there is a same DRL.
     *
     * @param drl Need to determine whether this DRL has been added
     * @return If the DRL already exists, return the DRL; if it does not exist, return {@code null}
     */
    public ExPayDRL contains(ExPayDRL drl) {
        if (amexDRLs.contains(drl)) {
            return drl;
        } else {
            for (ExPayDRL drl1 : amexDRLs) {
                if (Arrays.equals(drl.getProgramId(), drl1.getProgramId())) {
                    return drl1;
                }
            }
        }
        return null;
    }
}
