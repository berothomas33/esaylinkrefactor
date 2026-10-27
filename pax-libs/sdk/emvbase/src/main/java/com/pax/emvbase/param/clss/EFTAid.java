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
 * EFT Kernel AID
 */
public class EFTAid extends BaseAid {
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
     * Terminal capabilities
     */
    private byte[] termCapability;

    /**
     * Additional terminal capability bytes
     */
    private byte[] termAddCapability;

    /**
     * Kernel ID
     */
    private byte[] kernelId;


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
     * Get contactless transaction limit bytes.
     *
     * @return Contactless transaction limit bytes
     */
    public byte[] getTransLimitBytes() {
        return transLimitBytes;
    }

    /**
     * Set contactless transaction limit bytes.
     *
     * @param transLimitBytes Contactless transaction limit bytes
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
     * Get kernel ID.
     *
     * @return Kernel ID
     */
    public byte[] getKernelId() {
        return kernelId;
    }

    /**
     * Get kernel ID.
     *
     * @param kernelId Kernel ID
     */
    public void setKernelId(byte[] kernelId) {
        this.kernelId = kernelId;
    }
}
