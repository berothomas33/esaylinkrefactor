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
 * MIR Kernel AID
 */
public class MirAid extends BaseAid {
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
     * Contactless offline limit bytes
     */
    private byte[] floorLimitBytes;
    /**
     * Contactless transaction limit bytes
     */
    private byte[] transLimitBytes;
    /**
     * Contactless CVM limit bytes
     */
    private byte[] cvmLimitBytes;
    /**
     * Terminal Action Code – Denial (For refund transaction)
     */
    private byte[] refundTacDenial;
    /**
     * Contactless offline limit bytes (For refund transaction)
     */
    private byte[] refundFloorLimit;
    /**
     * Terminal TPM Capabilities, Tag DF55
     *
     * <ul>
     *     <li>Byte 1 (Leftmost) :
     *         <ul>
     *             <li>bit 8 : 1 - Online PIN supported</li>
     *             <li>bit 7 : 1 - Signature supported</li>
     *             <li>bit 6 : 1 - CD-CVM allowed (for POS-terminals always 1), 0 - CD-CVM Not
     *             supported (only for ATM)</li>
     *             <li>bit 5 : 0 - RFU</li>
     *             <li>bit 4 : 1 - EMV contact mode supported</li>
     *             <li>bit 3 : 1 - Offline-only terminal</li>
     *             <li>bit 2 : 1 - Terminal operates in Delayed Authorization mode</li>
     *             <li>bit 1 : 1 - Terminal is ATM</li>
     *         </ul>
     *     </li>
     *     <li>Byte 2 :
     *         <ul>
     *             <li>bit 8 : 1 - Terminal unable to go online</li>
     *             <li>bit 7 ~ 1 : 0 - RFU</li>
     *         </ul>
     *     </li>
     * </ul>
     */
    private byte[] termTPMCapability;
    /**
     * Tag DF810C
     */
    private byte[] tlvDF810CTag;
    /**
     * Transaction recovery limit
     */
    private byte[] transRecoveryLimit;
    /**
     * Data exchange tag list.
     * <p>
     *     The object contains a set of tags which the terminal expects to receive from the
     *     kernel during data exchange after processing the response to the SELECT ADF command.
     * </p>
     * <p>
     *     Recommended minimum set of tags: '5A'
     * </p>
     */
    private byte[] dataExchangeTagList;
    /**
     * Application interchange profile (AIP), Tag 82. Only for protocol 2.
     * <ul>
     *     <li>Byte 1 (Leftmost) :
     *         <ul>
     *             <li>bit 8 : 0 - RFU</li>
     *             <li>bit 7 : 0 - SDA support</li>
     *             <li>bit 6 : 0 - DDA support</li>
     *             <li>bit 5 : 1 - Cardholder verification supported</li>
     *             <li>bit 4 : 1 - Terminal Risk Management is required</li>
     *             <li>bit 3 : 0 - Issuer authentication is performed by External Authenticate
     *             command</li>
     *             <li>bit 2 : 0 - RFU</li>
     *             <li>bit 1 : 1 - CDA supported</li>
     *         </ul>
     *     </li>
     *     <li>Byte 2 :
     *         <ul>
     *             <li>bit 8 : 1 - Processing of contactless application in EMV mode</li>
     *             <li>bit 7 ~ 1 : 0 - RFU</li>
     *         </ul>
     *     </li>
     * </ul>
     */
    private byte[] protocol2Tag82;
    /**
     * Whether the PAN is found in the exception file list or not.
     *
     * <ul>
     *     <li>1 - Yes</li>
     *     <li>0 - No</li>
     * </ul>
     */
    private byte exceptFileFlag;


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
     * Get contactless offline limit bytes.
     *
     * @return Contactless offline limit bytes
     */
    public byte[] getFloorLimitBytes() {
        return floorLimitBytes;
    }

    /**
     * Set contactless offline limit bytes.
     *
     * @param floorLimitBytes Contactless offline limit bytes
     */
    public void setFloorLimitBytes(byte[] floorLimitBytes) {
        this.floorLimitBytes = floorLimitBytes;
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
     * Set contactless transaction limit (No On-device CVM) bytes.
     *
     * @param transLimitBytes Contactless transaction limit (No On-device CVM) bytes
     */
    public void setTransLimitBytes(byte[] transLimitBytes) {
        this.transLimitBytes = transLimitBytes;
    }

    /**
     * Get contactless CVM limit bytes.
     *
     * @return Contactless CVM limit bytes
     */
    public byte[] getCvmLimitBytes() {
        return cvmLimitBytes;
    }

    /**
     * Set contactless CVM limit bytes.
     *
     * @param cvmLimitBytes Contactless CVM limit bytes
     */
    public void setCvmLimitBytes(byte[] cvmLimitBytes) {
        this.cvmLimitBytes = cvmLimitBytes;
    }

    /**
     * Get Terminal Action Code – Denial (For refund transaction).
     *
     * @return Terminal Action Code – Denial
     */
    public byte[] getRefundTacDenial() {
        return refundTacDenial;
    }

    /**
     * Set Terminal Action Code - Denial (For refund transaction).
     *
     * @param refundTacDenial Terminal Action Code - Denial
     */
    public void setRefundTacDenial(byte[] refundTacDenial) {
        this.refundTacDenial = refundTacDenial;
    }

    /**
     * Get contactless offline limit bytes (For refund transaction).
     *
     * @return Contactless offline limit bytes
     */
    public byte[] getRefundFloorLimit() {
        return refundFloorLimit;
    }

    /**
     * Set contactless offline limit bytes (For refund transaction).
     *
     * @param refundFloorLimit Contactless offline limit bytes
     */
    public void setRefundFloorLimit(byte[] refundFloorLimit) {
        this.refundFloorLimit = refundFloorLimit;
    }

    /**
     * Get Terminal TPM Capabilities.
     *
     * @return Terminal TPM Capabilities
     * @see #termTPMCapability
     */
    public byte[] getTermTPMCapability() {
        return termTPMCapability;
    }

    /**
     * Set Terminal TPM Capabilities.
     *
     * @param termTPMCapability Terminal TPM Capabilities
     * @see #termTPMCapability
     */
    public void setTermTPMCapability(byte[] termTPMCapability) {
        this.termTPMCapability = termTPMCapability;
    }

    /**
     * Get Tag DF810C.
     *
     * @return Tag DF810C
     */
    public byte[] getTlvDF810CTag() {
        return tlvDF810CTag;
    }

    /**
     * Set Tag DF810C.
     *
     * @param tlvDF810CTag Tag DF810C
     */
    public void setTlvDF810CTag(byte[] tlvDF810CTag) {
        this.tlvDF810CTag = tlvDF810CTag;
    }

    /**
     * Get transaction recovery limit.
     *
     * @return Transaction recovery limit
     */
    public byte[] getTransRecoveryLimit() {
        return transRecoveryLimit;
    }

    /**
     * Set transaction recovery limit.
     *
     * @param transRecoveryLimit Transaction recovery limit
     */
    public void setTransRecoveryLimit(byte[] transRecoveryLimit) {
        this.transRecoveryLimit = transRecoveryLimit;
    }

    /**
     * Get data exchange tag list.
     *
     * @return Data exchange tag list
     * @see #dataExchangeTagList
     */
    public byte[] getDataExchangeTagList() {
        return dataExchangeTagList;
    }

    /**
     * Set data exchange tag list.
     *
     * @param dataExchangeTagList Data exchange tag list
     * @see #dataExchangeTagList
     */
    public void setDataExchangeTagList(byte[] dataExchangeTagList) {
        this.dataExchangeTagList = dataExchangeTagList;
    }

    /**
     * Get application interchange profile (AIP).
     *
     * @return Application interchange profile (AIP)
     * @see #protocol2Tag82
     */
    public byte[] getProtocol2Tag82() {
        return protocol2Tag82;
    }

    /**
     * Set application interchange profile (AIP).
     *
     * @param protocol2Tag82 Application interchange profile (AIP)
     * @see #protocol2Tag82
     */
    public void setProtocol2Tag82(byte[] protocol2Tag82) {
        this.protocol2Tag82 = protocol2Tag82;
    }

    /**
     * Get whether the PAN is found in the exception file list or not.
     *
     * @return PAN found in the exception file list flag
     * @see #exceptFileFlag
     */
    public byte getExceptFileFlag() {
        return exceptFileFlag;
    }

    /**
     * Set whether the PAN is found in the exception file list or not.
     *
     * @param exceptFileFlag PAN found in the exception file list flag
     * @see #exceptFileFlag
     */
    public void setExceptFileFlag(byte exceptFileFlag) {
        this.exceptFileFlag = exceptFileFlag;
    }
}
