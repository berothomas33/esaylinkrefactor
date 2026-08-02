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
 * PayPass Kernel AID
 */
public class PayPassAid extends BaseAid {
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
     * Kernel Configuration.
     *
     * Indicates the Kernel configuration options.
     * <ul>
     *     <li>bit 8: Mag-stripe mode contactless transactions not supported</li>
     *     <li>bit 7: EMV mode contactless transactions not supported</li>
     *     <li>bit 6: On device cardholder verification supported</li>
     *     <li>bit 5: Relay resistance protocol supported</li>
     *     <li>bit 4~1: Each bit RFU</li>
     * </ul>
     */
    private byte[] kernelConfig;

    /**
     * Contactless offline limit bytes
     */
    private byte[] floorLimitBytes;

    /**
     * Contactless transaction limit bytes (No On-device CVM)
     */
    private byte[] transLimitBytes;

    /**
     * Contactless transaction limit bytes (On-device CVM)
     */
    private byte[] cvmTransLimitBytes;

    /**
     * Contactless CVM limit bytes
     */
    private byte[] cvmLimitBytes;

    /**
     * Terminal Risk Management Data
     */
    private byte[] termRiskManage;

    /**
     * Card Data Input Capability
     */
    private byte[] cardDataInput;

    /**
     * CVM Capability – CVM Required
     * <br>
     * Indicates the CVM capability of the Terminal and Reader when the transaction amount is
     * greater than the Reader CVM Required Limit.
     * <ul>
     *     <li>bit 8: Plaintext PIN for ICC verification</li>
     *     <li>bit 7: Enciphered PIN for online verification</li>
     *     <li>bit 6: Signature (paper)</li>
     *     <li>bit 5: Enciphered PIN for offline verification</li>
     *     <li>bit 4: No CVM required</li>
     *     <li>bit 3~1: Each bit RFU</li>
     * </ul>
     */
    private byte[] cvmRequired;

    /**
     * CVM Capability – No CVM Required
     * <br>
     * Indicates the CVM capability of the Terminal and Reader when the transaction amount is
     * less than or equal to the Reader CVM Required Limit.
     * <ul>
     *     <li>bit 8: Plaintext PIN for ICC verification</li>
     *     <li>bit 7: Enciphered PIN for online verification</li>
     *     <li>bit 6: Signature (paper)</li>
     *     <li>bit 5: Enciphered PIN for offline verification</li>
     *     <li>bit 4: No CVM required</li>
     *     <li>bit 3~1: Each bit RFU</li>
     * </ul>
     */
    private byte[] noCvmRequired;

    /**
     * Security Capability
     *
     * Indicates the security capability of the Kernel.
     * <ul>
     *     <li>bit 8: SDA</li>
     *     <li>bit 7: DDA</li>
     *     <li>bit 6: Card capture</li>
     *     <li>bit 5: RFU</li>
     *     <li>bit 4: CDA</li>
     *     <li>bit 3~1: Each bit RFU</li>
     * </ul>
     */
    private byte[] securityCapability;

    private byte[] magVersion;

    /**
     * Mag-stripe CVM Capability – CVM Required
     */
    private byte[] magCvm;

    /**
     * Mag-stripe CVM Capability – No CVM Required
     */
    private byte[] magNoCvm;

    /**
     * Kernel ID
     */
    private byte[] kernelId;

    /**
     * Additional Terminal Capabilities.
     *
     * Indicates the data input and output capabilities of the Terminal and Reader.
     */
    private byte[] addCapability;

    /**
     * Data exchange support or not.
     *
     * 1 - Support Data Exchange
     * 0 - Not support Data Exchange
     */
    private byte dataExchangeSupportFlag;

    /**
     * {@code Clss_SetParam_MC()} method parameter.
     *
     * Used to set the {@code CLSS_PARAM_TIMER} parameter.
     */
    private byte[] tlvParam;

    /**
     * Default UDOL
     *
     * The Default UDOL is the UDOL to be used for constructing the value field of the COMPUTE
     * CRYPTOGRAPHIC CHECKSUM command if the UDOL in the Card is not present. The Default UDOL
     * must contain as its only entry the tag and length of the Unpredictable Number (Numeric)
     * and has the value: '9F6A04'.
     */
    private byte[] defaultUDOL;

    /**
     * Contactless offline limit bytes for Refund and Void transaction type
     */
    private byte[] refundVoidFloorLimit;

    /**
     * Terminal Action Code – Denial for Refund and Void transaction type
     */
    private byte[] refundVoidTacDenial;

    private boolean supportDefaultMcTermParam;
    private byte[] maxTornNum;
    private byte[] maxTornLifetime;
    private byte[] deviceSN;
    private byte[] dsOperatorId;


    public byte[] getTacDenial() {
        return tacDenial;
    }

    public void setTacDenial(byte[] tacDenial) {
        this.tacDenial = tacDenial;
    }

    public byte[] getTacOnline() {
        return tacOnline;
    }

    public void setTacOnline(byte[] tacOnline) {
        this.tacOnline = tacOnline;
    }

    public byte[] getTacDefault() {
        return tacDefault;
    }

    public void setTacDefault(byte[] tacDefault) {
        this.tacDefault = tacDefault;
    }

    public byte[] getKernelConfig() {
        return kernelConfig;
    }

    public void setKernelConfig(byte[] kernelConfig) {
        this.kernelConfig = kernelConfig;
    }

    public byte[] getFloorLimitBytes() {
        return floorLimitBytes;
    }

    public void setFloorLimitBytes(byte[] floorLimitBytes) {
        this.floorLimitBytes = floorLimitBytes;
    }

    public byte[] getTransLimitBytes() {
        return transLimitBytes;
    }

    public void setTransLimitBytes(byte[] transLimitBytes) {
        this.transLimitBytes = transLimitBytes;
    }

    public byte[] getCvmLimitBytes() {
        return cvmLimitBytes;
    }

    public void setCvmLimitBytes(byte[] cvmLimitBytes) {
        this.cvmLimitBytes = cvmLimitBytes;
    }

    public byte[] getTermRiskManage() {
        return termRiskManage;
    }

    public void setTermRiskManage(byte[] termRiskManage) {
        this.termRiskManage = termRiskManage;
    }

    public byte[] getCardDataInput() {
        return cardDataInput;
    }

    public void setCardDataInput(byte[] cardDataInput) {
        this.cardDataInput = cardDataInput;
    }

    public byte[] getCvmRequired() {
        return cvmRequired;
    }

    public void setCvmRequired(byte[] cvmRequired) {
        this.cvmRequired = cvmRequired;
    }

    public byte[] getNoCvmRequired() {
        return noCvmRequired;
    }

    public void setNoCvmRequired(byte[] noCvmRequired) {
        this.noCvmRequired = noCvmRequired;
    }

    public byte[] getSecurityCapability() {
        return securityCapability;
    }

    public void setSecurityCapability(byte[] securityCapability) {
        this.securityCapability = securityCapability;
    }

    public byte[] getMagVersion() {
        return magVersion;
    }

    public void setMagVersion(byte[] magVersion) {
        this.magVersion = magVersion;
    }

    public byte[] getMagCvm() {
        return magCvm;
    }

    public void setMagCvm(byte[] magCvm) {
        this.magCvm = magCvm;
    }

    public byte[] getMagNoCvm() {
        return magNoCvm;
    }

    public void setMagNoCvm(byte[] magNoCvm) {
        this.magNoCvm = magNoCvm;
    }

    public byte[] getKernelId() {
        return kernelId;
    }

    public void setKernelId(byte[] kernelId) {
        this.kernelId = kernelId;
    }

    public byte[] getAddCapability() {
        return addCapability;
    }

    public void setAddCapability(byte[] addCapability) {
        this.addCapability = addCapability;
    }

    public byte getDataExchangeSupportFlag() {
        return dataExchangeSupportFlag;
    }

    public void setDataExchangeSupportFlag(byte dataExchangeSupportFlag) {
        this.dataExchangeSupportFlag = dataExchangeSupportFlag;
    }

    public byte[] getTlvParam() {
        return tlvParam;
    }

    public void setTlvParam(byte[] tlvParam) {
        this.tlvParam = tlvParam;
    }

    public byte[] getDefaultUDOL() {
        return defaultUDOL;
    }

    public void setDefaultUDOL(byte[] defaultUDOL) {
        this.defaultUDOL = defaultUDOL;
    }

    public byte[] getRefundVoidFloorLimit() {
        return refundVoidFloorLimit;
    }

    public void setRefundVoidFloorLimit(byte[] refundVoidFloorLimit) {
        this.refundVoidFloorLimit = refundVoidFloorLimit;
    }

    public byte[] getRefundVoidTacDenial() {
        return refundVoidTacDenial;
    }

    public void setRefundVoidTacDenial(byte[] refundVoidTacDenial) {
        this.refundVoidTacDenial = refundVoidTacDenial;
    }

    public byte[] getCvmTransLimitBytes() {
        return cvmTransLimitBytes;
    }

    public void setCvmTransLimitBytes(byte[] cvmTransLimitBytes) {
        this.cvmTransLimitBytes = cvmTransLimitBytes;
    }

    public boolean isSupportDefaultMcTermParam() {
        return supportDefaultMcTermParam;
    }

    public void setSupportDefaultMcTermParam(boolean supportDefaultMcTermParam) {
        this.supportDefaultMcTermParam = supportDefaultMcTermParam;
    }

    public byte[] getMaxTornNum() {
        return maxTornNum;
    }

    public void setMaxTornNum(byte[] maxTornNum) {
        this.maxTornNum = maxTornNum;
    }

    public byte[] getMaxTornLifetime() {
        return maxTornLifetime;
    }

    public void setMaxTornLifetime(byte[] maxTornLifetime) {
        this.maxTornLifetime = maxTornLifetime;
    }

    public byte[] getDeviceSN() {
        return deviceSN;
    }

    public void setDeviceSN(byte[] deviceSN) {
        this.deviceSN = deviceSN;
    }

    public byte[] getDsOperatorId() {
        return dsOperatorId;
    }

    public void setDsOperatorId(byte[] dsOperatorId) {
        this.dsOperatorId = dsOperatorId;
    }
}
