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
 * DPAS Kernel AID
 */
public class DpasAid extends BaseAid {
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
     * Whether the PAN is found in the exception file list or not (EMV mode only). 0 = No, 1 = Yes.
     */
    private byte exceptFileFlag;

    /**
     * Tag 9F66, Terminal Transaction Qualifiers (TTQ)
     */
    private byte[] ttq;


    //////////////////////////////////////////////////////////////////////////////////////
    // Getter and setter /////////////////////////////////////////////////////////////////

    /**
     * Get PAN found in exception file flag
     *
     * @return Whether the PAN is found in the exception file list or not
     * @see #exceptFileFlag
     */
    public byte getExceptFileFlag() {
        return exceptFileFlag;
    }

    /**
     * Set PAN found in exception file flag
     *
     * @param exceptFileFlag Whether the PAN is found in the exception file list or not
     * @see #exceptFileFlag
     */
    public void setExceptFileFlag(byte exceptFileFlag) {
        this.exceptFileFlag = exceptFileFlag;
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
     * Get terminal transaction qualifiers
     *
     * @return Terminal transaction qualifiers
     * @see #ttq
     */
    public byte[] getTtq() {
        return ttq;
    }

    /**
     * Set terminal transaction qualifiers
     * @param ttq Terminal transaction qualifiers
     * @see #ttq
     */
    public void setTtq(byte[] ttq) {
        this.ttq = ttq;
    }
}
