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
    private String tDOL;
    private String version;
    private String terminalType;
    private String terminalCapability;
    private String terminalAdditionalCapability;
    private String termTransCap;
    private String unRange;
    private byte supportOptTrans;
    private byte delayAuthSupport;
    private String exPayRdCap;
    private String exFunction;
    private byte supportFullOnline;
    private String aucRFU;

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

    public String getdDOL() {
        return dDOL;
    }

    public void setdDOL(String dDOL) {
        this.dDOL = dDOL;
    }

    public byte[] getdDOLBytes() {
        return ConvertUtils.strToBcdPaddingRight(dDOL);
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

    public String getTermTransCap() {
        return termTransCap;
    }

    public void setTermTransCap(String termTransCap) {
        this.termTransCap = termTransCap;
    }

    public byte[] getTermTransCapBytes() {
        return ConvertUtils.strToBcdPaddingLeft(termTransCap);
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

    public String getExFunction() {
        return exFunction;
    }

    public void setExFunction(String exFunction) {
        this.exFunction = exFunction;
    }

    public byte[] getExFunctionBytes() {
        return ConvertUtils.strToBcdPaddingLeft(exFunction);
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
