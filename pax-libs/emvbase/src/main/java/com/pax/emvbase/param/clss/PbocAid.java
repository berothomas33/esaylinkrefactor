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
 * PBOC Kernel AID
 */
public class PbocAid extends BaseAid {
    private String aidValue;
    private byte[] TTQ;
    private byte[] termCapability;
    private byte[] termAddCapability;
    private byte quicsFlag;
    private long tornMaxLifeTime;
    private short tornLogMaxNum;
    private short tornSupport;
    private byte[] tornRFU;
    private byte[] aucRFU;
    private byte aidType;
    private long qpsLimit;

    public String getAidValue() {
        return aidValue;
    }

    public void setAidValue(String aidValue) {
        this.aidValue = aidValue;
    }

    public byte[] getTTQ() {
        return TTQ;
    }

    public void setTTQ(byte[] TTQ) {
        this.TTQ = TTQ;
    }

    public byte[] getTermAddCapability() {
        return termAddCapability;
    }

    public void setTermAddCapability(byte[] termAddCapability) {
        this.termAddCapability = termAddCapability;
    }

    public byte[] getTermCapability() {
        return termCapability;
    }

    public void setTermCapability(byte[] termCapability) {
        this.termCapability = termCapability;
    }

    public byte getQuicsFlag() {
        return quicsFlag;
    }

    public void setQuicsFlag(byte quicsFlag) {
        this.quicsFlag = quicsFlag;
    }

    public long getTornMaxLifeTime() {
        return tornMaxLifeTime;
    }

    public void setTornMaxLifeTime(long tornMaxLifeTime) {
        this.tornMaxLifeTime = tornMaxLifeTime;
    }

    public short getTornLogMaxNum() {
        return tornLogMaxNum;
    }

    public void setTornLogMaxNum(short tornLogMaxNum) {
        this.tornLogMaxNum = tornLogMaxNum;
    }

    public short getTornSupport() {
        return tornSupport;
    }

    public void setTornSupport(short tornSupport) {
        this.tornSupport = tornSupport;
    }

    public byte[] getTornRFU() {
        return tornRFU;
    }

    public void setTornRFU(byte[] tornRFU) {
        this.tornRFU = tornRFU;
    }

    public byte[] getAucRFU() {
        return aucRFU;
    }

    public void setAucRFU(byte[] aucRFU) {
        this.aucRFU = aucRFU;
    }

    public byte getAidType() {
        return aidType;
    }

    public void setAidType(byte aidType) {
        this.aidType = aidType;
    }

    public long getQpsLimit() {
        return qpsLimit;
    }

    public void setQpsLimit(long qpsLimit) {
        this.qpsLimit = qpsLimit;
    }
}
