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
    private String aid;
    private byte selFlag;
    private long transLimit;
    private byte transLimitFlag;
    private long floorLimit;
    private byte floorLimitFlag;
    private long cvmLimit;
    private byte cvmLimitFlag;
    private String tacDenial;
    private String tacOnline;
    private String tacDefault;
    private String acquirerId;
    @Transient
    private byte[] acquirerIdBytes;
    private String dDOL;
    private String version;
    private String terminalType;
    private String terminalCapability;
    private String terminalAdditionalCapability;
    private String addTagObjList;
    private String mandatoryTagObjList;
    private String authTransDataTagObjList;
    private String clssKernelCapability;
    private String clssPOSImplOption;
    private String appAuthTransType;
    private String refundTacDenial;
    private String timeout;
    private String memorySlotReadTemp;
    private String memorySlotUpdateTemp;
    private String impOption;
    private String checkExceptionFilePan;

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

    public String getAid() {
        return aid;
    }

    public void setAid(String aid) {
        this.aid = aid;
    }

    public byte[] getAidBytes() {
        return ConvertUtils.strToBcdPaddingRight(aid);
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

    public String getTacOnline() {
        return tacOnline;
    }

    public void setTacOnline(String tacOnline) {
        this.tacOnline = tacOnline;
    }

    public byte[] getTacOnlineBytes() {
        return ConvertUtils.strToBcdPaddingRight(tacOnline);
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public byte[] getVersionBytes() {
        return ConvertUtils.strToBcdPaddingRight(version);
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

    public String getTerminalCapability() {
        return terminalCapability;
    }

    public void setTerminalCapability(String terminalCapability) {
        this.terminalCapability = terminalCapability;
    }

    public byte[] getTerminalCapabilityBytes() {
        return ConvertUtils.strToBcdPaddingLeft(terminalCapability);
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

    public String getAddTagObjList() {
        return addTagObjList;
    }

    public void setAddTagObjList(String addTagObjList) {
        this.addTagObjList = addTagObjList;
    }

    public byte[] getAddTagObjListBytes() {
        return ConvertUtils.strToBcdPaddingLeft(addTagObjList);
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

    public String getAuthTransDataTagObjList() {
        return authTransDataTagObjList;
    }

    public void setAuthTransDataTagObjList(String authTransDataTagObjList) {
        this.authTransDataTagObjList = authTransDataTagObjList;
    }

    public byte[] getAuthTransDataTagObjListBytes() {
        return ConvertUtils.strToBcdPaddingLeft(authTransDataTagObjList);
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

    public String getClssPOSImplOption() {
        return clssPOSImplOption;
    }

    public void setClssPOSImplOption(String clssPOSImplOption) {
        this.clssPOSImplOption = clssPOSImplOption;
    }

    public byte[] getClssPOSImplOptionBytes() {
        return ConvertUtils.strToBcdPaddingLeft(clssPOSImplOption);
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

    public String getRefundTacDenial() {
        return refundTacDenial;
    }

    public void setRefundTacDenial(String refundTacDenial) {
        this.refundTacDenial = refundTacDenial;
    }

    public byte[] getRefundTacDenialBytes() {
        return ConvertUtils.strToBcdPaddingLeft(refundTacDenial);
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

    public String getMemorySlotReadTemp() {
        return memorySlotReadTemp;
    }

    public void setMemorySlotReadTemp(String memorySlotReadTemp) {
        this.memorySlotReadTemp = memorySlotReadTemp;
    }

    public byte[] getMemorySlotReadTempBytes() {
        return ConvertUtils.strToBcdPaddingLeft(memorySlotReadTemp);
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

    public String getImpOption() {
        return impOption;
    }

    public void setImpOption(String impOption) {
        this.impOption = impOption;
    }

    public byte getImpOptionBytes() {
        return ConvertUtils.strToBcdPaddingLeft(impOption)[0];
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

    public String getDDOL() {
        return this.dDOL;
    }

    public void setDDOL(String dDOL) {
        this.dDOL = dDOL;
    }
}
