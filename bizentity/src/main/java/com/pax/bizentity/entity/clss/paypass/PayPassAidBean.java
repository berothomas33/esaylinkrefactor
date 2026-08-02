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

package com.pax.bizentity.entity.clss.paypass;

import android.text.TextUtils;
import com.pax.commonlib.utils.ConvertUtils;
import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Transient;

@Entity(nameInDb = "paypass_aid")
public class PayPassAidBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String ID_FIELD_NAME = "paypass_id";
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
    private long cvmTransLimit;
    @Transient
    private byte[] cvmTransLimitBytes;
    private long floorLimit;
    @Transient
    private byte[] floorLimitBytes;
    private byte floorLimitFlag;
    private long cvmLimit;
    @Transient
    private byte[] cvmLimitBytes;
    private byte cvmLimitFlag;
    private String tacDenial;
    @Transient
    private byte[] tacDenialBytes;
    private String tacOnline;
    @Transient
    private byte[] tacOnlineBytes;
    private String tacDefault;
    @Transient
    private byte[] tacDefaultBytes;
    private String acquirerId;
    @Transient
    private byte[] acquirerIdBytes;
    private String version;
    @Transient
    private byte[] versionBytes;
    private String riskManageData;
    @Transient
    private byte[] riskManageDataBytes;
    private String terminalType;
    @Transient
    private byte terminalTypeByte;
    private String terminalAdditionalCapability;
    @Transient
    private byte[] terminalAdditionalCapabilityBytes;
    private String kernelConfig;
    @Transient
    private byte[] kernelConfigBytes;
    private String cardDataInput;
    @Transient
    private byte[] cardDataInputBytes;
    private String cvmRequired;
    @Transient
    private byte[] cvmRequiredBytes;
    private String noCvmRequired;
    @Transient
    private byte[] noCvmRequiredBytes;
    private String securityCapability;
    @Transient
    private byte[] securityCapabilityBytes;
    private String magVersion;
    @Transient
    private byte[] magVersionBytes;
    private String magCvm;
    @Transient
    private byte[] magCvmBytes;
    private String magNoCvm;
    @Transient
    private byte[] magNoCvmBytes;
    private String kernelId;
    private byte[] kernelIdBytes;
    private byte dataExchangeSupportFlag;
    private String tlvParam;
    @Transient
    private byte[] tlvParamBytes;
    private String defaultUDOL;
    @Transient
    private byte[] defaultUDOLBytes;
    private long refundVoidFloorLimit;
    private String refundVoidTacDenial;
    @Transient
    private byte[] refundVoidTacDenialBytes;
    @NotNull
    private boolean supportDefaultMcTermParam = true;
    private String maxTornNum;
    @Transient
    private byte[] maxTornNumBytes;
    private String maxTornLifetime;
    @Transient
    private byte[] maxTornLifetimeBytes;
    private String deviceSN;
    @Transient
    private byte[] deviceSNBytes;
    private String dsOperatorId;
    @Transient
    private byte[] dsOperatorIdBytes;


    @Generated(hash = 903366745)
    public PayPassAidBean(Long id, String appName, String aid, byte selFlag, long transLimit,
            byte transLimitFlag, long cvmTransLimit, long floorLimit, byte floorLimitFlag,
            long cvmLimit, byte cvmLimitFlag, String tacDenial, String tacOnline, String tacDefault,
            String acquirerId, String version, String riskManageData, String terminalType,
            String terminalAdditionalCapability, String kernelConfig, String cardDataInput,
            String cvmRequired, String noCvmRequired, String securityCapability, String magVersion,
            String magCvm, String magNoCvm, String kernelId, byte[] kernelIdBytes,
            byte dataExchangeSupportFlag, String tlvParam, String defaultUDOL,
            long refundVoidFloorLimit, String refundVoidTacDenial, boolean supportDefaultMcTermParam,
            String maxTornNum, String maxTornLifetime, String deviceSN, String dsOperatorId) {
        this.id = id;
        this.appName = appName;
        this.aid = aid;
        this.selFlag = selFlag;
        this.transLimit = transLimit;
        this.transLimitFlag = transLimitFlag;
        this.cvmTransLimit = cvmTransLimit;
        this.floorLimit = floorLimit;
        this.floorLimitFlag = floorLimitFlag;
        this.cvmLimit = cvmLimit;
        this.cvmLimitFlag = cvmLimitFlag;
        this.tacDenial = tacDenial;
        this.tacOnline = tacOnline;
        this.tacDefault = tacDefault;
        this.acquirerId = acquirerId;
        this.version = version;
        this.riskManageData = riskManageData;
        this.terminalType = terminalType;
        this.terminalAdditionalCapability = terminalAdditionalCapability;
        this.kernelConfig = kernelConfig;
        this.cardDataInput = cardDataInput;
        this.cvmRequired = cvmRequired;
        this.noCvmRequired = noCvmRequired;
        this.securityCapability = securityCapability;
        this.magVersion = magVersion;
        this.magCvm = magCvm;
        this.magNoCvm = magNoCvm;
        this.kernelId = kernelId;
        this.kernelIdBytes = kernelIdBytes;
        this.dataExchangeSupportFlag = dataExchangeSupportFlag;
        this.tlvParam = tlvParam;
        this.defaultUDOL = defaultUDOL;
        this.refundVoidFloorLimit = refundVoidFloorLimit;
        this.refundVoidTacDenial = refundVoidTacDenial;
        this.supportDefaultMcTermParam = supportDefaultMcTermParam;
        this.maxTornNum = maxTornNum;
        this.maxTornLifetime = maxTornLifetime;
        this.deviceSN = deviceSN;
        this.dsOperatorId = dsOperatorId;
    }
    @Generated(hash = 216977840)
    public PayPassAidBean() {
    }
    public Long getId() {
        return this.id;
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

    public long getCvmTransLimit() {
        return cvmTransLimit;
    }

    public void setCvmTransLimit(long cvmTransLimit) {
        this.cvmTransLimit = cvmTransLimit;
    }

    public byte[] getCvmTransLimitBytes() {
        String num = ConvertUtils.getPaddedNumber(cvmTransLimit, 12);
        return ConvertUtils.strToBcdPaddingLeft(num);
    }

    public void setCvmTransLimitBytes(byte[] cvmTransLimitBytes) {
        this.cvmTransLimitBytes = cvmTransLimitBytes;
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

    public byte[] getVersionBytes() {
        return ConvertUtils.strToBcdPaddingRight(version);
    }

    public void setVersionBytes(byte[] versionBytes) {
        this.versionBytes = versionBytes;
    }

    public String getRiskManageData() {
        return riskManageData;
    }

    public void setRiskManageData(String riskManageData) {
        this.riskManageData = riskManageData;
    }

    public byte[] getRiskManageDataBytes() {
        if (TextUtils.isEmpty(riskManageData)){
            return new byte[0];
        }
        return ConvertUtils.strToBcdPaddingLeft(riskManageData);
    }

    public void setRiskManageDataBytes(byte[] riskManageDataBytes) {
        this.riskManageDataBytes = riskManageDataBytes;
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

    public String getKernelConfig() {
        return kernelConfig;
    }

    public void setKernelConfig(String kernelConfig) {
        this.kernelConfig = kernelConfig;
    }

    public byte[] getKernelConfigBytes() {
        return ConvertUtils.strToBcdPaddingLeft(kernelConfig);
    }

    public void setKernelConfigBytes(byte[] kernelConfigBytes) {
        this.kernelConfigBytes = kernelConfigBytes;
    }

    public String getCardDataInput() {
        return cardDataInput;
    }

    public void setCardDataInput(String cardDataInput) {
        this.cardDataInput = cardDataInput;
    }

    public byte[] getCardDataInputBytes() {
        return ConvertUtils.strToBcdPaddingLeft(cardDataInput);
    }

    public void setCardDataInputBytes(byte[] cardDataInputBytes) {
        this.cardDataInputBytes = cardDataInputBytes;
    }

    public String getCvmRequired() {
        return cvmRequired;
    }

    public void setCvmRequired(String cvmRequired) {
        this.cvmRequired = cvmRequired;
    }

    public byte[] getCvmRequiredBytes() {
        return ConvertUtils.strToBcdPaddingLeft(cvmRequired);
    }

    public void setCvmRequiredBytes(byte[] cvmRequiredBytes) {
        this.cvmRequiredBytes = cvmRequiredBytes;
    }

    public String getNoCvmRequired() {
        return noCvmRequired;
    }

    public void setNoCvmRequired(String noCvmRequired) {
        this.noCvmRequired = noCvmRequired;
    }

    public byte[] getNoCvmRequiredBytes() {
        return ConvertUtils.strToBcdPaddingLeft(noCvmRequired);
    }

    public void setNoCvmRequiredBytes(byte[] noCvmRequiredBytes) {
        this.noCvmRequiredBytes = noCvmRequiredBytes;
    }

    public String getSecurityCapability() {
        return securityCapability;
    }

    public void setSecurityCapability(String securityCapability) {
        this.securityCapability = securityCapability;
    }

    public byte[] getSecurityCapabilityBytes() {
        return ConvertUtils.strToBcdPaddingLeft(securityCapability);
    }

    public void setSecurityCapabilityBytes(byte[] securityCapabilityBytes) {
        this.securityCapabilityBytes = securityCapabilityBytes;
    }

    public String getMagVersion() {
        return magVersion;
    }

    public void setMagVersion(String magVersion) {
        this.magVersion = magVersion;
    }

    public byte[] getMagVersionBytes() {
        return ConvertUtils.strToBcdPaddingLeft(magVersion);
    }

    public void setMagVersionBytes(byte[] magVersionBytes) {
        this.magVersionBytes = magVersionBytes;
    }

    public String getMagCvm() {
        return magCvm;
    }

    public void setMagCvm(String magCvm) {
        this.magCvm = magCvm;
    }

    public byte[] getMagCvmBytes() {
        return ConvertUtils.strToBcdPaddingLeft(magCvm);
    }

    public void setMagCvmBytes(byte[] magCvmBytes) {
        this.magCvmBytes = magCvmBytes;
    }

    public String getMagNoCvm() {
        return magNoCvm;
    }

    public void setMagNoCvm(String magNoCvm) {
        this.magNoCvm = magNoCvm;
    }

    public byte[] getMagNoCvmBytes() {
        return ConvertUtils.strToBcdPaddingLeft(magNoCvm);
    }

    public void setMagNoCvmBytes(byte[] magNoCvmBytes) {
        this.magNoCvmBytes = magNoCvmBytes;
    }

    public String getKernelId() {
        return kernelId;
    }

    public void setKernelId(String kernelId) {
        this.kernelId = kernelId;
    }

    public byte[] getKernelIdBytes() {
        return ConvertUtils.strToBcdPaddingLeft(kernelId);
    }

    public void setKernelIdBytes(byte[] kernelIdBytes) {
        this.kernelIdBytes = kernelIdBytes;
    }

    public byte getDataExchangeSupportFlag() {
        return dataExchangeSupportFlag;
    }

    public void setDataExchangeSupportFlag(byte dataExchangeSupportFlag) {
        this.dataExchangeSupportFlag = dataExchangeSupportFlag;
    }

    public String getTlvParam() {
        return tlvParam;
    }

    public void setTlvParam(String tlvParam) {
        this.tlvParam = tlvParam;
    }

    public byte[] getTlvParamBytes() {
        return ConvertUtils.strToBcdPaddingLeft(tlvParam);
    }

    public void setTlvParamBytes(byte[] tlvParamBytes) {
        this.tlvParamBytes = tlvParamBytes;
    }

    public String getDefaultUDOL() {
        return defaultUDOL;
    }

    public void setDefaultUDOL(String defaultUDOL) {
        this.defaultUDOL = defaultUDOL;
    }

    public byte[] getDefaultUDOLBytes() {
        return ConvertUtils.strToBcdPaddingLeft(defaultUDOL);
    }

    public void setDefaultUDOLBytes(byte[] defaultUDOLBytes) {
        this.defaultUDOLBytes = defaultUDOLBytes;
    }

    public long getRefundVoidFloorLimit() {
        return refundVoidFloorLimit;
    }

    public void setRefundVoidFloorLimit(long refundVoidFloorLimit) {
        this.refundVoidFloorLimit = refundVoidFloorLimit;
    }

    public String getRefundVoidTacDenial() {
        return refundVoidTacDenial;
    }

    public void setRefundVoidTacDenial(String refundVoidTacDenial) {
        this.refundVoidTacDenial = refundVoidTacDenial;
    }

    public byte[] getRefundVoidTacDenialBytes() {
        return ConvertUtils.strToBcdPaddingLeft(refundVoidTacDenial);
    }

    public void setRefundVoidTacDenialBytes(byte[] refundVoidTacDenialBytes) {
        this.refundVoidTacDenialBytes = refundVoidTacDenialBytes;
    }

    public String getMaxTornNum() {
        return maxTornNum;
    }

    public void setMaxTornNum(String maxTornNum) {
        this.maxTornNum = maxTornNum;
    }

    public byte[] getMaxTornNumBytes() {
        return ConvertUtils.strToBcdPaddingLeft(maxTornNum);
    }

    public void setMaxTornNumBytes(byte[] maxTornNumBytes) {
        this.maxTornNumBytes = maxTornNumBytes;
    }

    public String getMaxTornLifetime() {
        return maxTornLifetime;
    }

    public void setMaxTornLifetime(String maxTornLifetime) {
        this.maxTornLifetime = maxTornLifetime;
    }

    public byte[] getMaxTornLifetimeBytes() {
        return ConvertUtils.strToBcdPaddingLeft(maxTornLifetime);
    }

    public void setMaxTornLifetimeBytes(byte[] maxTornLifetimeBytes) {
        this.maxTornLifetimeBytes = maxTornLifetimeBytes;
    }

    public boolean getSupportDefaultMcTermParam() {
        return supportDefaultMcTermParam;
    }

    public void setSupportDefaultMcTermParam(boolean supportDefaultMcTermParam) {
        this.supportDefaultMcTermParam = supportDefaultMcTermParam;
    }

    public String getDeviceSN() {
        return deviceSN;
    }

    public void setDeviceSN(String deviceSN) {
        this.deviceSN = deviceSN;
    }

    public byte[] getDeviceSNBytes() {
        return ConvertUtils.strToBcdPaddingLeft(deviceSN);
    }

    public void setDeviceSNBytes(byte[] deviceSNBytes) {
        this.deviceSNBytes = deviceSNBytes;
    }

    public String getDsOperatorId() {
        return dsOperatorId;
    }

    public void setDsOperatorId(String dsOperatorId) {
        this.dsOperatorId = dsOperatorId;
    }

    public byte[] getDsOperatorIdBytes() {
        return ConvertUtils.strToBcdPaddingLeft(dsOperatorId);
    }

    public void setDsOperatorIdBytes(byte[] dsOperatorIdBytes) {
        this.dsOperatorIdBytes = dsOperatorIdBytes;
    }
}
