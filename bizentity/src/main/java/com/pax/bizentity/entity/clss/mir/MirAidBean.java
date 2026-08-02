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

package com.pax.bizentity.entity.clss.mir;

import android.text.TextUtils;
import com.pax.commonlib.utils.ConvertUtils;
import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Transient;

@Entity(nameInDb = "mir_aid")
public class MirAidBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String ID_FIELD_NAME = "mir_id";
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
    @Transient
    private byte[] transLimitBytes;
    private byte transLimitFlag;
    private long floorLimit;
    @Transient
    private byte[] floorLimitBytes;
    private byte floorLimitFlag;
    // Reader CVM required limit
    private long cvmLimit;
    @Transient
    private byte[] cvmLimitBytes;
    private byte cvmLimitFlag;
    //tag DF8121, Terminal Action Code – Denial
    private String tacDenial;
    @Transient
    private byte[] tacDenialBytes;
    //tag DF8122, Terminal Action Code – Online
    private String tacOnline;
    @Transient
    private byte[] tacOnlineBytes;
    //tag DF8120, Terminal Action Code – Default
    private String tacDefault;
    @Transient
    private byte[] tacDefaultBytes;
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
    private long refundFloorLimit;
    @Transient
    private byte[] refundFloorLimitBytes;
    private String refundTacDenial;
    @Transient
    private byte[] refundTacDenialBytes;
    private String termTPMCapability;
    @Transient
    private byte[] termTPMCapabilityBytes;
    private String tlvDF810CTag;
    @Transient
    private byte[] tlvDF810CTagBytes;
    private String transRecoveryLimit;
    @Transient
    private byte[] transRecoveryLimitBytes;
    private String dataExchangeTagList;
    @Transient
    private byte[] dataExchangeTagListBytes;
    private String protocol2Tag82;
    @Transient
    private byte[] protocol2Tag82Bytes;
    private byte exceptFileFlag;


    @Generated(hash = 1574384671)
    public MirAidBean(Long id, String appName, String aid, byte selFlag, long transLimit,
            byte transLimitFlag, long floorLimit, byte floorLimitFlag, long cvmLimit,
            byte cvmLimitFlag, String tacDenial, String tacOnline, String tacDefault,
            String acquirerId, String version, String terminalType, long refundFloorLimit,
            String refundTacDenial, String termTPMCapability, String tlvDF810CTag,
            String transRecoveryLimit, String dataExchangeTagList, String protocol2Tag82,
            byte exceptFileFlag) {
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
        this.tacDenial = tacDenial;
        this.tacOnline = tacOnline;
        this.tacDefault = tacDefault;
        this.acquirerId = acquirerId;
        this.version = version;
        this.terminalType = terminalType;
        this.refundFloorLimit = refundFloorLimit;
        this.refundTacDenial = refundTacDenial;
        this.termTPMCapability = termTPMCapability;
        this.tlvDF810CTag = tlvDF810CTag;
        this.transRecoveryLimit = transRecoveryLimit;
        this.dataExchangeTagList = dataExchangeTagList;
        this.protocol2Tag82 = protocol2Tag82;
        this.exceptFileFlag = exceptFileFlag;
    }

    @Generated(hash = 955416255)
    public MirAidBean() {
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

    public String getTacDenial() {
        return tacDenial;
    }

    public void setTacDenial(String tacDenial) {
        this.tacDenial = tacDenial;
    }

    public byte[] getTacDenialBytes() {
        return ConvertUtils.strToBcdPaddingRight(tacDenial);
    }

    public void setTacDenialBytes(byte[] tacDenialBytes) {
        this.tacDenialBytes = tacDenialBytes;
    }

    public String getTacOnline() {
        return tacOnline;
    }

    public void setTacOnline(String tacOnline) {
        this.tacOnline = tacOnline;
    }

    public byte[] getTacOnlineBytes() {
        return ConvertUtils.strToBcdPaddingRight(tacOnline);
    }

    public void setTacOnlineBytes(byte[] tacOnlineBytes) {
        this.tacOnlineBytes = tacOnlineBytes;
    }

    public String getTacDefault() {
        return tacDefault;
    }

    public void setTacDefault(String tacDefault) {
        this.tacDefault = tacDefault;
    }

    public byte[] getTacDefaultBytes() {
        return ConvertUtils.strToBcdPaddingRight(tacDefault);
    }

    public void setTacDefaultBytes(byte[] tacDefaultBytes) {
        this.tacDefaultBytes = tacDefaultBytes;
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

    public String getTerminalType() {
        return terminalType;
    }

    public void setTerminalType(String terminalType) {
        this.terminalType = terminalType;
    }

    public byte getTerminalTypeByte() {
        if (!TextUtils.isEmpty(terminalType)){
            return ConvertUtils.strToBcdPaddingLeft(terminalType)[0];
        } else {
            return (byte) -1;
        }
    }

    public void setTerminalTypeByte(byte terminalTypeByte) {
        this.terminalTypeByte = terminalTypeByte;
    }

    public byte[] getVersionBytes() {
        return ConvertUtils.strToBcdPaddingRight(version);
    }

    public void setVersionBytes(byte[] versionBytes) {
        this.versionBytes = versionBytes;
    }

    public long getRefundFloorLimit() {
        return refundFloorLimit;
    }

    public void setRefundFloorLimit(long refundFloorLimit) {
        this.refundFloorLimit = refundFloorLimit;
    }

    public byte[] getRefundFloorLimitBytes() {
        String num = ConvertUtils.getPaddedNumber(refundFloorLimit, 12);
        return ConvertUtils.strToBcdPaddingLeft(num);
    }

    public void setRefundFloorLimitBytes(byte[] refundFloorLimitBytes) {
        this.refundFloorLimitBytes = refundFloorLimitBytes;
    }

    public String getRefundTacDenial() {
        return refundTacDenial;
    }

    public void setRefundTacDenial(String refundTacDenial) {
        this.refundTacDenial = refundTacDenial;
    }

    public byte[] getRefundTacDenialBytes() {
        return ConvertUtils.strToBcdPaddingRight(refundTacDenial);
    }

    public void setRefundTacDenialBytes(byte[] refundTacDenialBytes) {
        this.refundTacDenialBytes = refundTacDenialBytes;
    }

    public String getTermTPMCapability() {
        return termTPMCapability;
    }

    public void setTermTPMCapability(String termTPMCapability) {
        this.termTPMCapability = termTPMCapability;
    }

    public byte[] getTermTPMCapabilityBytes() {
        return ConvertUtils.strToBcdPaddingRight(termTPMCapability);
    }

    public void setTermTPMCapabilityBytes(byte[] termTPMCapabilityBytes) {
        this.termTPMCapabilityBytes = termTPMCapabilityBytes;
    }

    public String getTlvDF810CTag() {
        return tlvDF810CTag;
    }

    public void setTlvDF810CTag(String tlvDF810CTag) {
        this.tlvDF810CTag = tlvDF810CTag;
    }

    public byte[] getTlvDF810CTagBytes() {
        return ConvertUtils.strToBcdPaddingRight(tlvDF810CTag);
    }

    public void setTlvDF810CTagBytes(byte[] tlvDF810CTagBytes) {
        this.tlvDF810CTagBytes = tlvDF810CTagBytes;
    }

    public String getTransRecoveryLimit() {
        return transRecoveryLimit;
    }

    public void setTransRecoveryLimit(String transRecoveryLimit) {
        this.transRecoveryLimit = transRecoveryLimit;
    }

    public byte[] getTransRecoveryLimitBytes() {
        return ConvertUtils.strToBcdPaddingRight(transRecoveryLimit);
    }

    public void setTransRecoveryLimitBytes(byte[] transRecoveryLimitBytes) {
        this.transRecoveryLimitBytes = transRecoveryLimitBytes;
    }

    public String getDataExchangeTagList() {
        return dataExchangeTagList;
    }

    public void setDataExchangeTagList(String dataExchangeTagList) {
        this.dataExchangeTagList = dataExchangeTagList;
    }

    public byte[] getDataExchangeTagListBytes() {
        return ConvertUtils.strToBcdPaddingRight(dataExchangeTagList);
    }

    public void setDataExchangeTagListBytes(byte[] dataExchangeTagListBytes) {
        this.dataExchangeTagListBytes = dataExchangeTagListBytes;
    }

    public String getProtocol2Tag82() {
        return protocol2Tag82;
    }

    public void setProtocol2Tag82(String protocol2Tag82) {
        this.protocol2Tag82 = protocol2Tag82;
    }

    public byte[] getProtocol2Tag82Bytes() {
        return ConvertUtils.strToBcdPaddingRight(protocol2Tag82);
    }

    public void setProtocol2Tag82Bytes(byte[] protocol2Tag82Bytes) {
        this.protocol2Tag82Bytes = protocol2Tag82Bytes;
    }

    public byte getExceptFileFlag() {
        return exceptFileFlag;
    }

    public void setExceptFileFlag(byte exceptFileFlag) {
        this.exceptFileFlag = exceptFileFlag;
    }
}
