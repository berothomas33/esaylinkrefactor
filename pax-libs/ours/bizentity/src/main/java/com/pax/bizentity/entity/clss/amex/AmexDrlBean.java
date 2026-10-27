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

package com.pax.bizentity.entity.clss.amex;

import android.text.TextUtils;
import com.pax.commonlib.utils.ConvertUtils;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Transient;
@Entity(nameInDb = "amex_drl")
public class AmexDrlBean {

    /**
     * programId : 00000001
     * transLimit : 1500
     * transLimitFlag : 1
     * cvmLimit : 1000
     * cvmLimitFlag : 1
     * floorLimit : 1200
     * floorLimitFlag : 1
     * statusCheckFlag : 1
     * amountZeroNoAllowed : 1
     * dynamicLimitSet : 255
     */

    private String programId;
    @Transient
    private byte[] programIdBytes;
    @Transient
    private byte programIdLen;
    private long transLimit;
    @Transient
    private byte[] transLimitBytes;
    private byte transLimitFlag;
    private long cvmLimit;
    @Transient
    private byte[] cvmLimitBytes;
    private byte cvmLimitFlag;
    private long floorLimit;
    @Transient
    private byte[] floorLimitBytes;
    private byte floorLimitFlag;
    private byte statusCheckFlag;
    private byte amountZeroNoAllowed;
    private byte dynamicLimitSet;

    @Generated(hash = 817774059)
    public AmexDrlBean(String programId, long transLimit, byte transLimitFlag, long cvmLimit,
            byte cvmLimitFlag, long floorLimit, byte floorLimitFlag, byte statusCheckFlag,
            byte amountZeroNoAllowed, byte dynamicLimitSet) {
        this.programId = programId;
        this.transLimit = transLimit;
        this.transLimitFlag = transLimitFlag;
        this.cvmLimit = cvmLimit;
        this.cvmLimitFlag = cvmLimitFlag;
        this.floorLimit = floorLimit;
        this.floorLimitFlag = floorLimitFlag;
        this.statusCheckFlag = statusCheckFlag;
        this.amountZeroNoAllowed = amountZeroNoAllowed;
        this.dynamicLimitSet = dynamicLimitSet;
    }

    @Generated(hash = 1414908975)
    public AmexDrlBean() {
    }

    public String getProgramId() {
        return programId;
    }

    public void setProgramId(String programId) {
        this.programId = programId;
    }

    public byte[] getProgramIdBytes() {
        return ConvertUtils.strToBcdPaddingLeft(programId);
    }

    public void setProgramIdBytes(byte[] programIdBytes) {
        this.programIdBytes = programIdBytes;
    }

    public byte getProgramIdLen() {
        if (TextUtils.isEmpty(programId)){
            return 0;
        }
        return (byte) programId.length();
    }

    public void setProgramIdLen(byte programIdLen) {
        this.programIdLen = programIdLen;
    }

    public long getTransLimit() {
        return transLimit;
    }

    public void setTransLimit(long transLimit) {
        this.transLimit = transLimit;
    }

    public byte[] getTransLimitBytes() {
        String num = ConvertUtils.getPaddedNumber(transLimit, 12);
        return ConvertUtils.strToBcdPaddingLeft(num);
    }

    public void setTransLimitBytes(byte[] transLimitBytes) {
        this.transLimitBytes = transLimitBytes;
    }

    public byte getTransLimitFlag() {
        return transLimitFlag;
    }

    public void setTransLimitFlag(byte transLimitFlag) {
        this.transLimitFlag = transLimitFlag;
    }

    public long getCvmLimit() {
        return cvmLimit;
    }

    public void setCvmLimit(long cvmLimit) {
        this.cvmLimit = cvmLimit;
    }

    public byte[] getCvmLimitBytes() {
        String num = ConvertUtils.getPaddedNumber(cvmLimit, 12);
        return ConvertUtils.strToBcdPaddingLeft(num);
    }

    public void setCvmLimitBytes(byte[] cvmLimitBytes) {

        this.cvmLimitBytes = cvmLimitBytes;
    }

    public byte getCvmLimitFlag() {
        return cvmLimitFlag;
    }

    public void setCvmLimitFlag(byte cvmLimitFlag) {
        this.cvmLimitFlag = cvmLimitFlag;
    }

    public long getFloorLimit() {
        return floorLimit;
    }

    public void setFloorLimit(long floorLimit) {
        this.floorLimit = floorLimit;
    }

    public byte[] getFloorLimitBytes() {
        String num = ConvertUtils.getPaddedNumber(floorLimit, 12);
        return ConvertUtils.strToBcdPaddingLeft(num);
    }

    public void setFloorLimitBytes(byte[] floorLimitBytes) {
        this.floorLimitBytes = floorLimitBytes;
    }

    public byte getFloorLimitFlag() {
        return floorLimitFlag;
    }

    public void setFloorLimitFlag(byte floorLimitFlag) {
        this.floorLimitFlag = floorLimitFlag;
    }

    public byte getStatusCheckFlag() {
        return statusCheckFlag;
    }

    public void setStatusCheckFlag(byte statusCheckFlag) {
        this.statusCheckFlag = statusCheckFlag;
    }

    public byte getAmountZeroNoAllowed() {
        return amountZeroNoAllowed;
    }

    public void setAmountZeroNoAllowed(byte amountZeroNoAllowed) {
        this.amountZeroNoAllowed = amountZeroNoAllowed;
    }

    public byte getDynamicLimitSet() {
        return dynamicLimitSet;
    }

    public void setDynamicLimitSet(byte dynamicLimitSet) {
        this.dynamicLimitSet = dynamicLimitSet;
    }
}
