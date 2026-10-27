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
 * 2021/06/22                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

/**
 * AMEX Kernel AID
 */
public class AmexAid extends BaseAid {

    /**
     * Terminal Action Code – Denial
     */
    private byte[] tacDenial;

    /**
     * Terminal Action Code – Online
     */
    private byte[] tacOnline;

    /**
     * Terminal Action Code – Default
     */
    private byte[] tacDefault;

    /**
     * Terminal capabilities
     */
    private byte[] termCapability;

    /**
     * The terminal whether to support the optimization mode transaction flag
     */
    private byte supportOptTrans;

    /**
     * Specifies the range in which the unpredictable number must be generated in
     */
    private byte[] unRange = new byte[0];

    /**
     * tag 9F6E, Terminal Transaction Capabilities
     */
    private byte[] termTransCap;

    /**
     * Delayed authorization support flag.
     *
     * <ul>
     *     <li>1: support Delayed Authorization</li>
     *     <li>0: not support Delayed Authorization</li>
     * </ul>
     */
    private byte delayAuthSupport;

    /**
     * tag 9F6D A proprietary data element with bits 8, 7, and 4 only used to indicate a terminal’s
     * capability to support Kernel 4 mag-stripe or EMV contactless.
     */
    private byte exPayRdCap;

    /**
     * tag 9F49 Discretionary Data Object List
     */
    private byte[] dDOL;

    /**
     * tag 97 Transaction Certificate Data Object List
     */
    private byte[] tDOL;

    /**
     * Additional terminal capability bytes
     */
    private byte[] termAddCapability;

    /**
     * Data pointer of reader's relative parameters. Current bytes in used:
     * <br>
     * Byte 1:
     * <ul>
     *     <li>0 - support AE 3.0 (default)</li>
     *     <li>1 - support AE 3.1</li>
     * </ul>
     */
    private byte[] exFunction;

    /**
     * As the third parameter of the {@code Clss_AddReaderParam_AE} class constructor
     */
    private byte[] aucRFU;

    /**
     * Terminal support full online sign or not
     * <ul>
     *     <li>1 - Support</li>
     *     <li>0 - Do not support</li>
     * </ul>
     */
    private byte supportFullOnline;


    //////////////////////////////////////////////////////////////////////////////////////
    // Getter and setter /////////////////////////////////////////////////////////////////

    /**
     * Get Terminal Action Code – Denial.
     *
     * @return Terminal Action Code – Denial
     */
    public byte[] getTacDenial() {
        return tacDenial;
    }

    /**
     * Set Terminal Action Code – Denial.
     *
     * @param tacDenial Terminal Action Code – Denial
     */
    public void setTacDenial(byte[] tacDenial) {
        this.tacDenial = tacDenial;
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
     * Set Terminal Action Code – Online.
     *
     * @param tacOnline Terminal Action Code – Online
     */
    public void setTacOnline(byte[] tacOnline) {
        this.tacOnline = tacOnline;
    }

    /**
     * Get Terminal Action Code – Default.
     *
     * @return Terminal Action Code – Default
     */
    public byte[] getTacDefault() {
        return tacDefault;
    }

    /**
     * Set Terminal Action Code – Default.
     *
     * @param tacDefault Terminal Action Code – Default
     */
    public void setTacDefault(byte[] tacDefault) {
        this.tacDefault = tacDefault;
    }

    /**
     * Get terminal capabilities bytes.
     *
     * @return Terminal capabilities bytes
     */
    public byte[] getTermCapability() {
        return termCapability;
    }

    /**
     * Set terminal capabilities bytes.
     *
     * @param termCapability Terminal capabilities bytes
     */
    public void setTermCapability(byte[] termCapability) {
        this.termCapability = termCapability;
    }

    /**
     * Get support the optimization mode transaction flag.
     *
     * @return Support the optimization mode transaction flag
     */
    public byte getSupportOptTrans() {
        return supportOptTrans;
    }

    /**
     * Set support the optimization mode transaction flag.
     *
     * @param supportOptTrans Support the optimization mode transaction flag
     */
    public void setSupportOptTrans(byte supportOptTrans) {
        this.supportOptTrans = supportOptTrans;
    }

    /**
     * Get unpredictable number range.
     *
     * @return Unpredictable number range
     * @see #unRange
     */
    public byte[] getUnRange() {
        return unRange;
    }

    /**
     * Set unpredictable number range.
     *
     * @param unRange Unpredictable number range
     * @see #unRange
     */
    public void setUnRange(byte[] unRange) {
        this.unRange = unRange;
    }

    /**
     * Get terminal transaction capabilities.
     *
     * @return Terminal transaction capabilities
     * @see #termTransCap
     */
    public byte[] getTermTransCap() {
        return termTransCap;
    }

    /**
     * Set terminal transaction capabilities.
     *
     * @param termTransCap Terminal transaction capabilities
     * @see #termTransCap
     */
    public void setTermTransCap(byte[] termTransCap) {
        this.termTransCap = termTransCap;
    }

    /**
     * Get delayed authorization support flag.
     *
     * @return Delay authorization support flag
     * @see #delayAuthSupport
     */
    public byte getDelayAuthSupport() {
        return delayAuthSupport;
    }

    /**
     * Set delayed authorization support flag.
     *
     * @param delayAuthSupport Delay authorization support flag
     * @see #delayAuthSupport
     */
    public void setDelayAuthSupport(byte delayAuthSupport) {
        this.delayAuthSupport = delayAuthSupport;
    }

    /**
     * Get contactless reader capabilities.
     *
     * @return Contactless reader capabilities
     * @see #exPayRdCap
     */
    public byte getExPayRdCap() {
        return exPayRdCap;
    }

    /**
     * Set contactless reader capabilities.
     *
     * @param exPayRdCap Contactless reader capabilities
     * @see #exPayRdCap
     */
    public void setExPayRdCap(byte exPayRdCap) {
        this.exPayRdCap = exPayRdCap;
    }

    /**
     * Get discretionary data object list.
     *
     * @return Discretionary data object list
     * @see #dDOL
     */
    public byte[] getdDOL() {
        return dDOL;
    }

    /**
     * Set discretionary data object list.
     *
     * @param dDOL Discretionary data object list
     * @see #dDOL
     */
    public void setdDOL(byte[] dDOL) {
        this.dDOL = dDOL;
    }

    /**
     * Get transaction certificate data object list.
     *
     * @return Transaction certificate data object list
     * @see #tDOL
     */
    public byte[] gettDOL() {
        return tDOL;
    }

    /**
     * Set transaction certificate data object list.
     *
     * @param tDOL Transaction certificate data object list
     * @see #tDOL
     */
    public void settDOL(byte[] tDOL) {
        this.tDOL = tDOL;
    }

    /**
     * Get additional terminal capability bytes.
     *
     * @return Additional terminal capability bytes
     */
    public byte[] getTermAddCapability() {
        return termAddCapability;
    }

    /**
     * Set additional terminal capability bytes.
     *
     * @param termAddCapability Additional terminal capability bytes
     */
    public void setTermAddCapability(byte[] termAddCapability) {
        this.termAddCapability = termAddCapability;
    }

    /**
     * Get extend function parameter.
     *
     * <p>
     *     This parameter will be used as a parameter of the {@code Clss_SetExtendFunction_AE()} method
     * </p>
     *
     * @return Extend function parameter
     * @see #exFunction
     */
    public byte[] getExFunction() {
        return exFunction;
    }

    /**
     * Set extend function parameter.
     *
     * <p>
     *     This parameter will be used as a parameter of the {@code Clss_SetExtendFunction_AE()} method
     * </p>
     *
     * @param exFunction Extend function parameter
     * @see #exFunction
     */
    public void setExFunction(byte[] exFunction) {
        this.exFunction = exFunction;
    }

    /**
     * Get the third parameter of the {@code Clss_AddReaderParam_AE} class constructor.
     *
     * @return Third parameter of the {@code Clss_AddReaderParam_AE} class constructor
     */
    public byte[] getAucRFU() {
        return aucRFU;
    }

    /**
     * Set the third parameter of the {@code Clss_AddReaderParam_AE} class constructor.
     *
     * @param aucRFU Third parameter of the {@code Clss_AddReaderParam_AE} class constructor
     */
    public void setAucRFU(byte[] aucRFU) {
        this.aucRFU = aucRFU;
    }

    /**
     * Get support full online flag.
     *
     * @return Support full online flag
     * @see #supportFullOnline
     */
    public byte getSupportFullOnline() {
        return supportFullOnline;
    }

    /**
     * Set support full online flag.
     *
     * @param supportFullOnline Support full online flag
     * @see #supportFullOnline
     */
    public void setSupportFullOnline(byte supportFullOnline) {
        this.supportFullOnline = supportFullOnline;
    }
}
