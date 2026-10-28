package com.pax.bizentity.entity.clss.paywave;

import java.io.Serializable;

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
