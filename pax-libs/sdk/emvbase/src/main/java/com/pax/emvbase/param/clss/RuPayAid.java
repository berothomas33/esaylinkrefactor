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
 * RuPay Kernel AID
 */
public class RuPayAid extends BaseAid {
    // Terminal Action Code – Denial
    private byte[] tacDenial;

    // Terminal Action Code – Online
    private byte[] tacOnline;

    // Terminal Action Code – Default
    private byte[] tacDefault;
    private byte[] termCapability;
    private byte[] floorLimitBytes;
    private byte[] transLimitBytes;
    private byte[] cvmLimitBytes;
    private byte[] addCapability;
    private byte[] serviceIdBytes;
    private byte crypto17Flag;
    private byte zeroAmountNoAllowed;
    private byte statusCheckFlag;
    private byte[] TTQ;

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

    public byte[] getTermCapability() {
        return termCapability;
    }

    public void setTermCapability(byte[] termCapability) {
        this.termCapability = termCapability;
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

    public byte[] getAddCapability() {
        return addCapability;
    }

    public void setAddCapability(byte[] addCapability) {
        this.addCapability = addCapability;
    }

    public byte[] getServiceIdBytes() {
        return serviceIdBytes;
    }

    public void setServiceIdBytes(byte[] serviceIdBytes) {
        this.serviceIdBytes = serviceIdBytes;
    }

    public byte getCrypto17Flag() {
        return crypto17Flag;
    }

    public void setCrypto17Flag(byte crypto17Flag) {
        this.crypto17Flag = crypto17Flag;
    }

    public byte getZeroAmountNoAllowed() {
        return zeroAmountNoAllowed;
    }

    public void setZeroAmountNoAllowed(byte zeroAmountNoAllowed) {
        this.zeroAmountNoAllowed = zeroAmountNoAllowed;
    }

    public byte getStatusCheckFlag() {
        return statusCheckFlag;
    }

    public void setStatusCheckFlag(byte statusCheckFlag) {
        this.statusCheckFlag = statusCheckFlag;
    }

    public byte[] getTTQ() {
        return TTQ;
    }

    public void setTTQ(byte[] TTQ) {
        this.TTQ = TTQ;
    }
}
