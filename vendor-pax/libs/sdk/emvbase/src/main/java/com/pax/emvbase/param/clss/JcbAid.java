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
 * JCB Kernel AID
 */
public class JcbAid extends BaseAid {
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
     * Contactless transaction limit (No On-device CVM) bytes
     */
    private byte[] transLimitBytes;

    /**
     * Contactless transaction limit (On-device CVM) bytes
     */
    private byte[] cvmTransLimitBytes;

    /**
     * Contactless CVM limit bytes
     */
    private byte[] cvmLimitBytes;

    /**
     * Terminal interchange profile
     *
     * <ul>
     *     <li>Byte 1 (Leftmost) :
     *         <ul>
     *             <li>bit 8 : 1 - CVM required by reader / N/A</li>
     *             <li>bit 7 : 1 - Signature supported</li>
     *             <li>bit 6 : 1 - Online PIN supported</li>
     *             <li>bit 5 : 1 - On-Device CVM supported</li>
     *             <li>bit 4 : 0 - RFU</li>
     *             <li>bit 3 : 1 - Reader is a Transit Reader</li>
     *             <li>bit 2 : 1 - EMV contact chip supported</li>
     *             <li>bit 1 : 1 - (Contact Chip) Offline PIN supported</li>
     *         </ul>
     *     </li>
     *     <li>Byte 2 :
     *         <ul>
     *             <li>bit 8 : 1 - Issuer Update supported</li>
     *             <li>bit 7 ~ 1 : 0 - RFU</li>
     *         </ul>
     *     </li>
     *     <li>Byte 3 (Rightmost) : Each bit RFU</li>
     * </ul>
     */
    private byte[] termInterchange;

    /**
     * Terminal Compatibility Indicator.
     *
     * <ul>
     *     <li>bit 8 ~ 3 : RFU, should be set to 0</li>
     *     <li>bit 2 : 1 - EMV Mode Supported</li>
     *     <li>bit 1 : 1 - Magstripe Mode Supported</li>
     * </ul>
     */
    private byte[] termCompatFlag;

    /**
     * Combination Option, FF8130 Tag
     *
     * <ul>
     *     <li>Byte 1 (Leftmost) :
     *          <ul>
     *              <li>bit 8 : 0 - RFU</li>
     *              <li>bit 7 : 1 - Status Check supported</li>
     *              <li>bit 6 : 1 - Offline Data Authentication supported</li>
     *              <li>bit 5 : 1 - Exception File Check required</li>
     *              <li>bit 4 : 1 - Random Transaction Selection supported</li>
     *              <li>bit 3 : 0 - RFU</li>
     *              <li>bit 2 : 1 - EMV Mode Supported</li>
     *              <li>bit 1 : 1 - Legacy Mode Supported</li>
     *          </ul>
     *     </li>
     *     <li>Byte 2 (Rightmost) : Each bit RFU</li>
     * </ul>
     */
    private byte[] combinationOption;


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
     * Get terminal interchange profile.
     *
     * @return Terminal interchange profile
     * @see #termInterchange
     */
    public byte[] getTermInterchange() {
        return termInterchange;
    }

    /**
     * Set terminal interchange profile.
     *
     * @param termInterchange Terminal interchange profile
     * @see #termInterchange
     */
    public void setTermInterchange(byte[] termInterchange) {
        this.termInterchange = termInterchange;
    }

    /**
     * Get terminal compatibility indicator.
     *
     * @return Terminal compatibility indicator
     * @see #termCompatFlag
     */
    public byte[] getTermCompatFlag() {
        return termCompatFlag;
    }

    /**
     * Set terminal compatibility indicator.
     *
     * @param termCompatFlag Terminal compatibility indicator
     * @see #termCompatFlag
     */
    public void setTermCompatFlag(byte[] termCompatFlag) {
        this.termCompatFlag = termCompatFlag;
    }

    /**
     * Get combination option.
     *
     * @return Combination option
     * @see #combinationOption
     */
    public byte[] getCombinationOption() {
        return combinationOption;
    }

    /**
     * Set combination option.
     *
     * @param combinationOption Combination option
     * @see #combinationOption
     */
    public void setCombinationOption(byte[] combinationOption) {
        this.combinationOption = combinationOption;
    }

    /**
     * Get contactless transaction limit (On-device CVM) bytes.
     *
     * @return Contactless transaction limit (On-device CVM) bytes
     */
    public byte[] getCvmTransLimitBytes() {
        return cvmTransLimitBytes;
    }

    /**
     * Set contactless transaction limit (On-device CVM) bytes.
     *
     * @param cvmTransLimitBytes Contactless transaction limit (On-device CVM) bytes
     */
    public void setCvmTransLimitBytes(byte[] cvmTransLimitBytes) {
        this.cvmTransLimitBytes = cvmTransLimitBytes;
    }
}
