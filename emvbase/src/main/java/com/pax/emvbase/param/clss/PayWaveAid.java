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

import java.util.List;

/**
 * PayWave Kernel AID
 */
public class PayWaveAid extends BaseAid {
    private byte[] TTQ;

    /**
     * Floor limit by transaction type
     */
    private List<PayWaveInterFloorLimit> PayWaveInterFloorLimitList;
    private byte crypto17Flag;
    private byte statusCheckFlag;
    private byte zeroAmountNoAllowed;

    //tag 9F33 byte 3, Security Capability
    private byte securityCapability;
    private byte domesticOnly;
    private byte enDDAVerNo;


    public byte[] getTTQ() {
        return TTQ;
    }

    public void setTTQ(byte[] TTQ) {
        this.TTQ = TTQ;
    }

    public List<PayWaveInterFloorLimit> getPayWaveInterFloorLimitList() {
        return PayWaveInterFloorLimitList;
    }

    public void setPayWaveInterFloorLimitList(
            List<PayWaveInterFloorLimit> payWaveInterFloorLimitList) {
        PayWaveInterFloorLimitList = payWaveInterFloorLimitList;
    }

    public byte getCrypto17Flag() {
        return crypto17Flag;
    }

    public void setCrypto17Flag(byte crypto17Flag) {
        this.crypto17Flag = crypto17Flag;
    }

    public byte getStatusCheckFlag() {
        return statusCheckFlag;
    }

    public void setStatusCheckFlag(byte statusCheckFlag) {
        this.statusCheckFlag = statusCheckFlag;
    }

    public byte getZeroAmountNoAllowed() {
        return zeroAmountNoAllowed;
    }

    public void setZeroAmountNoAllowed(byte zeroAmountNoAllowed) {
        this.zeroAmountNoAllowed = zeroAmountNoAllowed;
    }

    public byte getSecurityCapability() {
        return securityCapability;
    }

    public void setSecurityCapability(byte securityCapability) {
        this.securityCapability = securityCapability;
    }

    public byte getDomesticOnly() {
        return domesticOnly;
    }

    public void setDomesticOnly(byte domesticOnly) {
        this.domesticOnly = domesticOnly;
    }

    public byte getEnDDAVerNo() {
        return enDDAVerNo;
    }

    public void setEnDDAVerNo(byte enDDAVerNo) {
        this.enDDAVerNo = enDDAVerNo;
    }
}
