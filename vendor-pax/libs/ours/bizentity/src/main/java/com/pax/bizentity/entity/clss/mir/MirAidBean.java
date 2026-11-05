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
    private String aid;
    private byte selFlag;
    private long transLimit;
    @Transient
    private byte[] transLimitBytes;
    private byte transLimitFlag;
    private long floorLimit;
    @Transient
    private byte[] floorLimitBytes;
    private byte floorLimitFlag;
    private long cvmLimit;
    @Transient
    private byte[] cvmLimitBytes;
    private byte cvmLimitFlag;
    private String tacDenial;
    private String tacOnline;
    private String tacDefault;
    private String acquirerId;
    @Transient
    private byte[] acquirerIdBytes;
    private String version;
    private String terminalType;
    private long refundFloorLimit;
    private String refundTacDenial;
    private String termTPMCapability;
    private String tlvDF810CTag;
    private String transRecoveryLimit;
    private String dataExchangeTagList;
    private String protocol2Tag82;
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

    public byte[] getVersionBytes() {
        return ConvertUtils.strToBcdPaddingRight(version);
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

    public String getRefundTacDenial() {
        return refundTacDenial;
    }

    public void setRefundTacDenial(String refundTacDenial) {
        this.refundTacDenial = refundTacDenial;
    }

    public byte[] getRefundTacDenialBytes() {
        return ConvertUtils.strToBcdPaddingRight(refundTacDenial);
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

    public String getTlvDF810CTag() {
        return tlvDF810CTag;
    }

    public void setTlvDF810CTag(String tlvDF810CTag) {
        this.tlvDF810CTag = tlvDF810CTag;
    }

    public byte[] getTlvDF810CTagBytes() {
        return ConvertUtils.strToBcdPaddingRight(tlvDF810CTag);
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

    public String getDataExchangeTagList() {
        return dataExchangeTagList;
    }

    public void setDataExchangeTagList(String dataExchangeTagList) {
        this.dataExchangeTagList = dataExchangeTagList;
    }

    public byte[] getDataExchangeTagListBytes() {
        return ConvertUtils.strToBcdPaddingRight(dataExchangeTagList);
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

    public byte getExceptFileFlag() {
        return exceptFileFlag;
    }

    public void setExceptFileFlag(byte exceptFileFlag) {
        this.exceptFileFlag = exceptFileFlag;
    }
}
