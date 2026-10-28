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
import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Transient;

@Entity(nameInDb = "amex_aid")
public class AmexAidBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String ID_FIELD_NAME = "amex_id";
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
    private String dDOL;
    @Transient
    private byte[] dDOLBytes;
    private String tDOL;
    @Transient
    private byte[] tDOLBytes;
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
    //tag: 9F40. Indicates the data input and output capabilities of the terminal
    private String terminalAdditionalCapability;
    @Transient
    private byte[] terminalAdditionalCapabilityBytes;
    //tag 9F6E, Terminal Transaction Capabilities
    private String termTransCap;
    @Transient
    private byte[] termTransCapBytes;
    //Specifies the range in which the unpredictable number must be generated in
    private String unRange;
    @Transient
    private byte[] unRangeBytes;
    //the terminal whether to support the optimization mode transaction flag
    private byte supportOptTrans;
    //1: support Delayed Authorization; 0: not support Delayed Authorization
    private byte delayAuthSupport;
    //tag 9F6D A proprietary data element with bits 8, 7, and 4 only used to indicate a terminal’s capability to support Kernel 4 mag-stripe or EMV contactless.
    private String exPayRdCap;
    @Transient
    private byte exPayRdCapByte;
    //Data pointer of reader’s relative parameters.Current bytes in used:Byte 1,0:support AE 3.0 (default) ,1:support AE 3.1
    private String exFunction;
    @Transient
    private byte[] exFunctionBytes;
    // support: 1, do not support: 0
    private byte supportFullOnline;
    private String aucRFU;
    @Transient
    private byte[] aucRFUBytes;

    @Generated(hash = 1594119186)
    public AmexAidBean() {
    }

    @Generated(hash = 870632631)
    public AmexAidBean(Long id, String appName, String aid, byte selFlag, long transLimit, byte transLimitFlag, long floorLimit, byte floorLimitFlag, long cvmLimit,
            byte cvmLimitFlag, String tacDenial, String tacOnline, String tacDefault, String acquirerId, String dDOL, String tDOL, String version,
            String terminalType, String terminalCapability, String terminalAdditionalCapability, String termTransCap, String unRange, byte supportOptTrans,
            byte delayAuthSupport, String exPayRdCap, String exFunction, byte supportFullOnline, String aucRFU) {
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
        this.dDOL = dDOL;
        this.tDOL = tDOL;
        this.version = version;
        this.terminalType = terminalType;
        this.terminalCapability = terminalCapability;
        this.terminalAdditionalCapability = terminalAdditionalCapability;
        this.termTransCap = termTransCap;
        this.unRange = unRange;
        this.supportOptTrans = supportOptTrans;
        this.delayAuthSupport = delayAuthSupport;
        this.exPayRdCap = exPayRdCap;
        this.exFunction = exFunction;
        this.supportFullOnline = supportFullOnline;
        this.aucRFU = aucRFU;
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

    public String getdDOL() {
        return dDOL;
    }

    public void setdDOL(String dDOL) {
        this.dDOL = dDOL;
    }

    public byte[] getdDOLBytes() {
        return ConvertUtils.strToBcdPaddingRight(dDOL);
    }

    public void setdDOLBytes(byte[] dDOLBytes) {
        this.dDOLBytes = dDOLBytes;
    }

    public String gettDOL() {
        return tDOL;
    }

    public void settDOL(String tDOL) {
        this.tDOL = tDOL;
    }

    public byte[] gettDOLBytes() {
        return ConvertUtils.strToBcdPaddingRight(tDOL);
    }

    public void settDOLBytes(byte[] tDOLBytes) {
        this.tDOLBytes = tDOLBytes;
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

    public String getTermTransCap() {
        return termTransCap;
    }

    public void setTermTransCap(String termTransCap) {
        this.termTransCap = termTransCap;
    }

    public byte[] getTermTransCapBytes() {
        return ConvertUtils.strToBcdPaddingLeft(termTransCap);
    }

    public void setTermTransCapBytes(byte[] termTransCapBytes) {
        this.termTransCapBytes = termTransCapBytes;
    }

    public String getUnRange() {
        return unRange;
    }

    public void setUnRange(String unRange) {
        this.unRange = unRange;
    }

    public byte[] getUnRangeBytes() {
        return ConvertUtils.strToBcdPaddingLeft(unRange);
    }

    public void setUnRangeBytes(byte[] unRangeBytes) {
        this.unRangeBytes = unRangeBytes;
    }

    public byte getSupportOptTrans() {
        return supportOptTrans;
    }

    public void setSupportOptTrans(byte supportOptTrans) {
        this.supportOptTrans = supportOptTrans;
    }

    public byte getDelayAuthSupport() {
        return delayAuthSupport;
    }

    public void setDelayAuthSupport(byte delayAuthSupport) {
        this.delayAuthSupport = delayAuthSupport;
    }

    public String getExPayRdCap() {
        return exPayRdCap;
    }

    public void setExPayRdCap(String exPayRdCap) {
        this.exPayRdCap = exPayRdCap;
    }

    public byte getExPayRdCapByte() {
        if (!TextUtils.isEmpty(exPayRdCap)){
            return ConvertUtils.strToBcdPaddingLeft(exPayRdCap)[0];
        }else {
            return (byte) 0;
        }
    }

    public void setExPayRdCapByte(byte exPayRdCapByte) {
        this.exPayRdCapByte = exPayRdCapByte;
    }

    public String getExFunction() {
        return exFunction;
    }

    public void setExFunction(String exFunction) {
        this.exFunction = exFunction;
    }

    public byte[] getExFunctionBytes() {
        return ConvertUtils.strToBcdPaddingLeft(exFunction);
    }

    public void setExFunctionBytes(byte[] exFunctionBytes) {
        this.exFunctionBytes = exFunctionBytes;
    }

    public byte getSupportFullOnline() {
        return supportFullOnline;
    }

    public void setSupportFullOnline(byte supportFullOnline) {
        this.supportFullOnline = supportFullOnline;
    }

    public String getAucRFU() {
        return aucRFU;
    }

    public void setAucRFU(String aucRFU) {
        this.aucRFU = aucRFU;
    }

    public byte[] getAucRFUBytes() {
        if (TextUtils.isEmpty(aucRFU)){
            return new byte[0];
        }
        return ConvertUtils.strToBcdPaddingLeft(aucRFU);
    }

    public void setAucRFUBytes(byte[] aucRFUBytes) {
        this.aucRFUBytes = aucRFUBytes;
    }

    public String getDDOL() {
        return this.dDOL;
    }

    public void setDDOL(String dDOL) {
        this.dDOL = dDOL;
    }

    public String getTDOL() {
        return this.tDOL;
    }

    public void setTDOL(String tDOL) {
        this.tDOL = tDOL;
    }
}
