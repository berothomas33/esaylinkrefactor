/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2019-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date	                Author	               Action
 * 20210707 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.bizentity.entity.clss.paywave;

import java.io.Serializable;

/**
 * Contactless limits for one transaction type (9C) of a Visa AID. Not a table of its own: the
 * list for an AID is stored as JSON in paywave_aid.FLOOR_LIMITS ({@link FloorLimitsConverter}).
 */
public class PayWaveInterFloorLimitBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private byte transType;
    private long floorLimit;
    private long transLimit;
    private long cvmLimit;
    private byte transLimitFlag;
    private byte cvmLimitFlag;
    private byte floorLimitFlag;

    public PayWaveInterFloorLimitBean(byte transType, long floorLimit, long transLimit,
            long cvmLimit, byte transLimitFlag, byte cvmLimitFlag, byte floorLimitFlag) {
        this.transType = transType;
        this.floorLimit = floorLimit;
        this.transLimit = transLimit;
        this.cvmLimit = cvmLimit;
        this.transLimitFlag = transLimitFlag;
        this.cvmLimitFlag = cvmLimitFlag;
        this.floorLimitFlag = floorLimitFlag;
    }

    public PayWaveInterFloorLimitBean() {
    }

    public byte getTransType() {
        return transType;
    }

    public void setTransType(byte transType) {
        this.transType = transType;
    }

    public long getFloorLimit() {
        return floorLimit;
    }

    public void setFloorLimit(long floorLimit) {
        this.floorLimit = floorLimit;
    }

    public long getTransLimit() {
        return transLimit;
    }

    public void setTransLimit(long transLimit) {
        this.transLimit = transLimit;
    }

    public long getCvmLimit() {
        return cvmLimit;
    }

    public void setCvmLimit(long cvmLimit) {
        this.cvmLimit = cvmLimit;
    }

    public byte getTransLimitFlag() {
        return transLimitFlag;
    }

    public void setTransLimitFlag(byte transLimitFlag) {
        this.transLimitFlag = transLimitFlag;
    }

    public byte getCvmLimitFlag() {
        return cvmLimitFlag;
    }

    public void setCvmLimitFlag(byte cvmLimitFlag) {
        this.cvmLimitFlag = cvmLimitFlag;
    }

    public byte getFloorLimitFlag() {
        return floorLimitFlag;
    }

    public void setFloorLimitFlag(byte floorLimitFlag) {
        this.floorLimitFlag = floorLimitFlag;
    }
}
