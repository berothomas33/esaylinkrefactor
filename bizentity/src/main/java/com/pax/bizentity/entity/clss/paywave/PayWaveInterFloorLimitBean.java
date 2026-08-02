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
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;

@Entity(nameInDb = "paywave_floor_limit")
public class PayWaveInterFloorLimitBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String ID_FIELD_NAME = "paywave_floor_limit_id";
    private static final String FOREIGN_KEY = "paywaveAidId";
    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    private Long id;
    @Property(nameInDb = FOREIGN_KEY)
    private Long paywaveAidId;
    private byte transType;
    private long floorLimit;
    private long transLimit;
    private long cvmLimit;
    private byte transLimitFlag;
    private byte cvmLimitFlag;
    private byte floorLimitFlag;

    @Generated(hash = 627473974)
    public PayWaveInterFloorLimitBean(Long id, Long paywaveAidId, byte transType, long floorLimit,
            long transLimit, long cvmLimit, byte transLimitFlag, byte cvmLimitFlag,
            byte floorLimitFlag) {
        this.id = id;
        this.paywaveAidId = paywaveAidId;
        this.transType = transType;
        this.floorLimit = floorLimit;
        this.transLimit = transLimit;
        this.cvmLimit = cvmLimit;
        this.transLimitFlag = transLimitFlag;
        this.cvmLimitFlag = cvmLimitFlag;
        this.floorLimitFlag = floorLimitFlag;
    }

    @Generated(hash = 1224974087)
    public PayWaveInterFloorLimitBean() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPaywaveAidId() {
        return paywaveAidId;
    }

    public void setPaywaveAidId(Long paywaveAidId) {
        this.paywaveAidId = paywaveAidId;
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
