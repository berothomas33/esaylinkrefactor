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

package com.pax.bizentity.entity.clss.pboc;

import android.text.TextUtils;
import com.pax.commonlib.utils.ConvertUtils;
import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Transient;

@Entity(nameInDb = "pboc_aid")
public class PBOCAidBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String ID_FIELD_NAME = "pboc_id";
    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    private Long id;

    private String appName;
    @Transient
    private byte[] appNameBytes;
    private String aid;
    @Transient
    private byte[] aidBytes;
    // Flag indicates whether the AID supports partial name selection or not.
    // 1: Does Not Support.
    // 0: Supports
    private byte selFlag;
    // Reader contactless transaction limit (No On-device CVM)
    private long transLimit;
    private byte transLimitFlag;
    private long floorLimit;
    private byte floorLimitFlag;
    // Reader CVM required limit
    private long cvmLimit;
    private byte cvmLimitFlag;
    private long qpsLimit;
    private String acquirerId;
    @Transient
    private byte[] acquirerIdBytes;
    /**
     * application version
     */
    private String version;
    @Transient
    private byte[] versionBytes;
    private String terminalType;
    @Transient
    private byte terminalTypeByte;
    /**
     * tag: 9F33. Indicates the card data input, CVM, and security capabilities of the terminal
     */
    private String terminalCapability;
    @Transient
    private byte[] terminalCapabilityBytes;
    private String terminalAdditionalCapability;
    @Transient
    private byte[] terminalAdditionalCapabilityBytes;

    private byte quicsFlag;
    private long tornMaxLifeTime;
    private long tornLogMaxNum;
    private byte tornSupport;
    private String tornRFU;
    @Transient
    private byte[] tornRFUBytes;
    private String aucRFU;
    @Transient
    private byte[] aucRFUBytes;
    private String ttq;
    @Transient
    private byte[] ttqBytes;
    private byte aidType;

    @Generated(hash = 1852646161)
    public PBOCAidBean(Long id, String appName, String aid, byte selFlag, long transLimit,
            byte transLimitFlag, long floorLimit, byte floorLimitFlag, long cvmLimit, byte cvmLimitFlag,
            long qpsLimit, String acquirerId, String version, String terminalType,
            String terminalCapability, String terminalAdditionalCapability, byte quicsFlag,
            long tornMaxLifeTime, long tornLogMaxNum, byte tornSupport, String tornRFU, String aucRFU,
            String ttq, byte aidType) {
        this.id = id;
        this.appName = appName;
        this.aid = aid;
        this.selFlag = selFlag;
        this.transLimit = transLimit;
        this.transLimitFlag = transLimitFlag;
        this.floorLimit = floorLimit;
        this.floorLimitFlag = floorLimitFlag;
        this.cvmLimit = cvmLimit;
        this.cvmLimitFlag = cvmLimitFlag;
        this.qpsLimit = qpsLimit;
        this.acquirerId = acquirerId;
        this.version = version;
        this.terminalType = terminalType;
        this.terminalCapability = terminalCapability;
        this.terminalAdditionalCapability = terminalAdditionalCapability;
        this.quicsFlag = quicsFlag;
        this.tornMaxLifeTime = tornMaxLifeTime;
        this.tornLogMaxNum = tornLogMaxNum;
        this.tornSupport = tornSupport;
        this.tornRFU = tornRFU;
        this.aucRFU = aucRFU;
        this.ttq = ttq;
        this.aidType = aidType;
    }

    @Generated(hash = 1759452647)
    public PBOCAidBean() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public byte[] getAppNameBytes() {
        return ConvertUtils.strToBcdPaddingLeft(appName);
    }

    public void setAppNameBytes(byte[] appNameBytes) {
        this.appNameBytes = appNameBytes;
    }

    public String getAid() {
        return aid;
    }

    public void setAid(String aid) {
        this.aid = aid;
    }

    public byte[] getAidBytes() {
        return ConvertUtils.strToBcdPaddingRight(aid);
    }

    public void setAidBytes(byte[] aidBytes) {
        this.aidBytes = aidBytes;
    }

    public byte getSelFlag() {
        return selFlag;
    }

    public void setSelFlag(byte selFlag) {
        this.selFlag = selFlag;
    }

    public long getTransLimit() {
        return transLimit;
    }

    public void setTransLimit(long transLimit) {
        this.transLimit = transLimit;
    }

    public byte getTransLimitFlag() {
        return transLimitFlag;
    }

    public void setTransLimitFlag(byte transLimitFlag) {
        this.transLimitFlag = transLimitFlag;
    }

    public long getFloorLimit() {
        return floorLimit;
    }

    public void setFloorLimit(long floorLimit) {
        this.floorLimit = floorLimit;
    }

    public byte getFloorLimitFlag() {
        return floorLimitFlag;
    }

    public void setFloorLimitFlag(byte floorLimitFlag) {
        this.floorLimitFlag = floorLimitFlag;
    }

    public long getCvmLimit() {
        return cvmLimit;
    }

    public void setCvmLimit(long cvmLimit) {
        this.cvmLimit = cvmLimit;
    }

    public byte getCvmLimitFlag() {
        return cvmLimitFlag;
    }

    public void setCvmLimitFlag(byte cvmLimitFlag) {
        this.cvmLimitFlag = cvmLimitFlag;
    }

    public String getAcquirerId() {
        return acquirerId;
    }

    public void setAcquirerId(String acquirerId) {
        this.acquirerId = acquirerId;
    }

    public byte[] getAcquirerIdBytes() {
        if (!TextUtils.isEmpty(acquirerId)){
            return ConvertUtils.strToBcdPaddingLeft(acquirerId);
        }{
            return new byte[0];
        }
    }

    public void setAcquirerIdBytes(byte[] acquirerIdBytes) {
        this.acquirerIdBytes = acquirerIdBytes;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public byte[] getVersionBytes() {
        return ConvertUtils.strToBcdPaddingRight(version);
    }

    public void setVersionBytes(byte[] versionBytes) {
        this.versionBytes = versionBytes;
    }

    public String getTerminalType() {
        return terminalType;
    }

    public void setTerminalType(String terminalType) {
        this.terminalType = terminalType;
    }

    public byte getTerminalTypeByte() {
        if (!TextUtils.isEmpty(terminalType)){
            return ConvertUtils.strToBcdPaddingLeft(terminalType)[0];
        }else {
            return (byte) -1;
        }
    }

    public void setTerminalTypeByte(byte terminalTypeByte) {
        this.terminalTypeByte = terminalTypeByte;
    }

    public String getTerminalCapability() {
        return terminalCapability;
    }

    public void setTerminalCapability(String terminalCapability) {
        this.terminalCapability = terminalCapability;
    }

    public byte[] getTerminalCapabilityBytes() {
        return ConvertUtils.strToBcdPaddingLeft(terminalCapability);
    }

    public void setTerminalCapabilityBytes(byte[] terminalCapabilityBytes) {
        this.terminalCapabilityBytes = terminalCapabilityBytes;
    }

    public String getTerminalAdditionalCapability() {
        return terminalAdditionalCapability;
    }

    public void setTerminalAdditionalCapability(String terminalAdditionalCapability) {
        this.terminalAdditionalCapability = terminalAdditionalCapability;
    }

    public byte[] getTerminalAdditionalCapabilityBytes() {
        return ConvertUtils.strToBcdPaddingLeft(terminalAdditionalCapability);
    }

    public void setTerminalAdditionalCapabilityBytes(byte[] terminalAdditionalCapabilityBytes) {
        this.terminalAdditionalCapabilityBytes = terminalAdditionalCapabilityBytes;
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

    public long getTornLogMaxNum() {
        return tornLogMaxNum;
    }

    public void setTornLogMaxNum(long tornLogMaxNum) {
        this.tornLogMaxNum = tornLogMaxNum;
    }

    public byte getTornSupport() {
        return tornSupport;
    }

    public void setTornSupport(byte tornSupport) {
        this.tornSupport = tornSupport;
    }

    public String getTornRFU() {
        return tornRFU;
    }

    public void setTornRFU(String tornRFU) {
        this.tornRFU = tornRFU;
    }

    public byte[] getTornRFUBytes() {
        return ConvertUtils.strToBcdPaddingLeft(tornRFU);
    }

    public void setTornRFUBytes(byte[] tornRFUBytes) {
        this.tornRFUBytes = tornRFUBytes;
    }

    public String getAucRFU() {
        return aucRFU;
    }

    public void setAucRFU(String aucRFU) {
        this.aucRFU = aucRFU;
    }

    public byte[] getAucRFUBytes() {
        return ConvertUtils.strToBcdPaddingLeft(aucRFU);
    }

    public void setAucRFUBytes(byte[] aucRFUBytes) {
        this.aucRFUBytes = aucRFUBytes;
    }

    public String getTtq() {
        return ttq;
    }

    public void setTtq(String ttq) {
        this.ttq = ttq;
    }

    public byte[] getTtqBytes() {
        return ConvertUtils.strToBcdPaddingLeft(ttq);
    }

    public void setTtqBytes(byte[] ttqBytes) {
        this.ttqBytes = ttqBytes;
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
