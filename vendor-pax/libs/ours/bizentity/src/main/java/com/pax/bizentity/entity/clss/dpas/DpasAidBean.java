package com.pax.bizentity.entity.clss.dpas;

import android.text.TextUtils;
import com.pax.commonlib.utils.ConvertUtils;
import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Transient;

@Entity(nameInDb = "dpas_aid")
public class DpasAidBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String ID_FIELD_NAME = "dpas_id";
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
    private String version;
    private String terminalType;
    private String terminalCapability;
    private byte exceptFileFlag;
    private String ttq;

    @Generated(hash = 1837212660)
    public DpasAidBean(Long id, String appName, String aid, byte selFlag, long transLimit,
            byte transLimitFlag, long floorLimit, byte floorLimitFlag, long cvmLimit, byte cvmLimitFlag,
            String tacDenial, String tacOnline, String tacDefault, String acquirerId, String version,
            String terminalType, String terminalCapability, byte exceptFileFlag, String ttq) {
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
        this.terminalCapability = terminalCapability;
        this.exceptFileFlag = exceptFileFlag;
        this.ttq = ttq;
    }

    @Generated(hash = 1932827618)
    public DpasAidBean() {
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

    public byte getExceptFileFlag() {
        return exceptFileFlag;
    }

    public void setExceptFileFlag(byte exceptFileFlag) {
        this.exceptFileFlag = exceptFileFlag;
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

}
