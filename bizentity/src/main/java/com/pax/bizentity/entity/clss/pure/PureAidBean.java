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
 * 20210705 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.bizentity.entity.clss.pure;

import android.text.TextUtils;
import com.pax.commonlib.utils.ConvertUtils;
import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Transient;

@Entity(nameInDb = "pure_aid")
public class PureAidBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String ID_FIELD_NAME = "pure_id";
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
    private String addTagObjList;
    @Transient
    private byte[] addTagObjListBytes;
    private String mandatoryTagObjList;
    @Transient
    private byte[] mandatoryTagObjListBytes;
    private String authTransDataTagObjList;
    @Transient
    private byte[] authTransDataTagObjListBytes;
    private String clssKernelCapability;
    @Transient
    private byte[] clssKernelCapabilityBytes;
    private String clssPOSImplOption;
    @Transient
    private byte[] clssPOSImplOptionBytes;
    private String appAuthTransType;
    @Transient
    private byte[] appAuthTransTypeBytes;
    private String refundTacDenial;
    @Transient
    private byte[] refundTacDenialBytes;
    private String timeout;
    @Transient
    private byte[] timeoutBytes;
    private String memorySlotReadTemp;
    @Transient
    private byte[] memorySlotReadTempBytes;
    private String memorySlotUpdateTemp;
    @Transient
    private byte[] memorySlotUpdateTempBytes;
    private String impOption;
    @Transient
    private byte impOptionBytes;
    private String checkExceptionFilePan;
    @Transient
    private byte[] checkExceptionFilePanBytes;

    @Generated(hash = 403985070)
    public PureAidBean(Long id, String appName, String aid, byte selFlag, long transLimit,
            byte transLimitFlag, long floorLimit, byte floorLimitFlag, long cvmLimit, byte cvmLimitFlag,
            String tacDenial, String tacOnline, String tacDefault, String acquirerId, String dDOL,
            String version, String terminalType, String terminalCapability,
            String terminalAdditionalCapability, String addTagObjList, String mandatoryTagObjList,
            String authTransDataTagObjList, String clssKernelCapability, String clssPOSImplOption,
            String appAuthTransType, String refundTacDenial, String timeout, String memorySlotReadTemp,
            String memorySlotUpdateTemp, String impOption, String checkExceptionFilePan) {
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
        this.version = version;
        this.terminalType = terminalType;
        this.terminalCapability = terminalCapability;
        this.terminalAdditionalCapability = terminalAdditionalCapability;
        this.addTagObjList = addTagObjList;
        this.mandatoryTagObjList = mandatoryTagObjList;
        this.authTransDataTagObjList = authTransDataTagObjList;
        this.clssKernelCapability = clssKernelCapability;
        this.clssPOSImplOption = clssPOSImplOption;
        this.appAuthTransType = appAuthTransType;
        this.refundTacDenial = refundTacDenial;
        this.timeout = timeout;
        this.memorySlotReadTemp = memorySlotReadTemp;
        this.memorySlotUpdateTemp = memorySlotUpdateTemp;
        this.impOption = impOption;
        this.checkExceptionFilePan = checkExceptionFilePan;
    }

    @Generated(hash = 652315165)
    public PureAidBean() {
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

    public byte[] getdDOLBytes() {
        return ConvertUtils.strToBcdPaddingRight(dDOL);
    }

    public void setdDOLBytes(byte[] dDOLBytes) {
        this.dDOLBytes = dDOLBytes;
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

    public String getAddTagObjList() {
        return addTagObjList;
    }

    public void setAddTagObjList(String addTagObjList) {
        this.addTagObjList = addTagObjList;
    }

    public byte[] getAddTagObjListBytes() {
        return ConvertUtils.strToBcdPaddingLeft(addTagObjList);
    }

    public void setAddTagObjListBytes(byte[] addTagObjListBytes) {
        this.addTagObjListBytes = addTagObjListBytes;
    }

    public String getMandatoryTagObjList() {
        return mandatoryTagObjList;
    }

    public void setMandatoryTagObjList(String mandatoryTagObjList) {
        this.mandatoryTagObjList = mandatoryTagObjList;
    }

    public byte[] getMandatoryTagObjListBytes() {
        return ConvertUtils.strToBcdPaddingLeft(mandatoryTagObjList);
    }

    public void setMandatoryTagObjListBytes(byte[] mandatoryTagObjListBytes) {
        this.mandatoryTagObjListBytes = mandatoryTagObjListBytes;
    }

    public String getAuthTransDataTagObjList() {
        return authTransDataTagObjList;
    }

    public void setAuthTransDataTagObjList(String authTransDataTagObjList) {
        this.authTransDataTagObjList = authTransDataTagObjList;
    }

    public byte[] getAuthTransDataTagObjListBytes() {
        return ConvertUtils.strToBcdPaddingLeft(authTransDataTagObjList);
    }

    public void setAuthTransDataTagObjListBytes(byte[] authTransDataTagObjListBytes) {
        this.authTransDataTagObjListBytes = authTransDataTagObjListBytes;
    }

    public String getClssKernelCapability() {
        return clssKernelCapability;
    }

    public void setClssKernelCapability(String clssKernelCapability) {
        this.clssKernelCapability = clssKernelCapability;
    }

    public byte[] getClssKernelCapabilityBytes() {
        return ConvertUtils.strToBcdPaddingLeft(clssKernelCapability);
    }

    public void setClssKernelCapabilityBytes(byte[] clssKernelCapabilityBytes) {
        this.clssKernelCapabilityBytes = clssKernelCapabilityBytes;
    }

    public String getClssPOSImplOption() {
        return clssPOSImplOption;
    }

    public void setClssPOSImplOption(String clssPOSImplOption) {
        this.clssPOSImplOption = clssPOSImplOption;
    }

    public byte[] getClssPOSImplOptionBytes() {
        return ConvertUtils.strToBcdPaddingLeft(clssPOSImplOption);
    }

    public void setClssPOSImplOptionBytes(byte[] clssPOSImplOptionBytes) {
        this.clssPOSImplOptionBytes = clssPOSImplOptionBytes;
    }

    public String getAppAuthTransType() {
        return appAuthTransType;
    }

    public void setAppAuthTransType(String appAuthTransType) {
        this.appAuthTransType = appAuthTransType;
    }

    public byte[] getAppAuthTransTypeBytes() {
        return ConvertUtils.strToBcdPaddingLeft(appAuthTransType);
    }

    public void setAppAuthTransTypeBytes(byte[] appAuthTransTypeBytes) {
        this.appAuthTransTypeBytes = appAuthTransTypeBytes;
    }

    public String getRefundTacDenial() {
        return refundTacDenial;
    }

    public void setRefundTacDenial(String refundTacDenial) {
        this.refundTacDenial = refundTacDenial;
    }

    public byte[] getRefundTacDenialBytes() {
        return ConvertUtils.strToBcdPaddingLeft(refundTacDenial);
    }

    public void setRefundTacDenialBytes(byte[] refundTacDenialBytes) {
        this.refundTacDenialBytes = refundTacDenialBytes;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public byte[] getTimeoutBytes() {
        return ConvertUtils.strToBcdPaddingLeft(timeout);
    }

    public void setTimeoutBytes(byte[] timeoutBytes) {
        this.timeoutBytes = timeoutBytes;
    }

    public String getMemorySlotReadTemp() {
        return memorySlotReadTemp;
    }

    public void setMemorySlotReadTemp(String memorySlotReadTemp) {
        this.memorySlotReadTemp = memorySlotReadTemp;
    }

    public byte[] getMemorySlotReadTempBytes() {
        return ConvertUtils.strToBcdPaddingLeft(memorySlotReadTemp);
    }

    public void setMemorySlotReadTempBytes(byte[] memorySlotReadTempBytes) {
        this.memorySlotReadTempBytes = memorySlotReadTempBytes;
    }

    public String getMemorySlotUpdateTemp() {
        return memorySlotUpdateTemp;
    }

    public void setMemorySlotUpdateTemp(String memorySlotUpdateTemp) {
        this.memorySlotUpdateTemp = memorySlotUpdateTemp;
    }

    public byte[] getMemorySlotUpdateTempBytes() {
        return ConvertUtils.strToBcdPaddingLeft(memorySlotUpdateTemp);
    }

    public void setMemorySlotUpdateTempBytes(byte[] memorySlotUpdateTempBytes) {
        this.memorySlotUpdateTempBytes = memorySlotUpdateTempBytes;
    }

    public String getImpOption() {
        return impOption;
    }

    public void setImpOption(String impOption) {
        this.impOption = impOption;
    }

    public byte getImpOptionBytes() {
        return ConvertUtils.strToBcdPaddingLeft(impOption)[0];
    }

    public void setImpOptionBytes(byte impOptionBytes) {
        this.impOptionBytes = impOptionBytes;
    }

    public String getCheckExceptionFilePan() {
        return checkExceptionFilePan;
    }

    public void setCheckExceptionFilePan(String checkExceptionFilePan) {
        this.checkExceptionFilePan = checkExceptionFilePan;
    }

    public byte[] getCheckExceptionFilePanBytes() {
        return ConvertUtils.strToBcdPaddingLeft(checkExceptionFilePan);
    }

    public void setCheckExceptionFilePanBytes(byte[] checkExceptionFilePanBytes) {
        this.checkExceptionFilePanBytes = checkExceptionFilePanBytes;
    }

    public String getDDOL() {
        return this.dDOL;
    }

    public void setDDOL(String dDOL) {
        this.dDOL = dDOL;
    }
}
