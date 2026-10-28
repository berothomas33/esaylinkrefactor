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
 * 20210508 	        xieYb                  Create
 * ===========================================================================================
 *
 */
package com.pax.bizentity.entity;

import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import com.alibaba.fastjson.annotation.JSONField;
import com.pax.commonlib.utils.ConvertUtils;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Transient;
import org.greenrobot.greendao.annotation.Unique;

@Entity(nameInDb = "aid")
public class EmvAid implements Serializable {
    private static final long serialVersionUID = 1L;
    @IntDef({PART_MATCH, FULL_MATCH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SelType {
    }

    public static final int PART_MATCH = 0;
    public static final int FULL_MATCH = 1;

    public static final String ID_FIELD_NAME = "id";
    public static final String AID_FIELD_NAME = "aid";


    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    @JSONField
    private Long id;
    /**
     * name
     */
    @NotNull
    private String appName;
    /**
     * aid
     */
    @Property(nameInDb = AID_FIELD_NAME)
    @NotNull
    @Unique
    private String aid;
    @Transient
    private byte[] aidBytes;
    /**
     * PART_MATCH/FULL_MATCH
     */
    @NotNull
    @SelType
    private int selFlag;
    /**
     * priority
     */
    @NotNull
    private int priority;
    /**
     * if enable online PIN
     */
    @NotNull
    private boolean onlinePin;

    /**
     * target percent
     */
    @NotNull
    @IntRange(from = 0, to = 100)
    private int targetPer;
    /**
     * max target percent
     */
    @NotNull
    @IntRange(from = 0, to = 100)
    private int maxTargetPer;
    /**
     * floor limit check flag
     * 0- don't check
     * 1- Check
     */
    @NotNull
    @IntRange(from = 0, to = 1)
    private int floorLimitCheckFlg;
    /**
     * do random transaction selection
     */
    @NotNull
    private boolean randTransSel;
    @Transient
    private byte randTransSelByte;
    /**
     * velocity check
     */
    @NotNull
    private boolean velocityCheck;
    @Transient
    private byte velocityCheckByte;
    /**
     * floor limit
     */
    @NotNull
    private long floorLimit;
    /**
     * threshold
     */
    @NotNull
    private long threshold;
    /**
     * TAC denial
     */
    private String tacDenial;
    @Transient
    private byte[] tacDenialBytes;
    /**
     * TAC online
     */
    private String tacOnline;
    @Transient
    private byte[] tacOnlineBytes;
    /**
     * TAC default
     */
    private String tacDefault;
    @Transient
    private byte[] tacDefaultBytes;
    /**
     * acquirer id
     */
    private String acquirerId;
    @Transient
    private byte[] acquirerIdBytes;
    /**
     * dDOL
     */
    private String dDOL;
    @Transient
    private byte[] dDOLBytes;
    /**
     * tDOL
     */
    private String tDOL;
    @Transient
    private byte[] tDOLBytes;
    /**
     * application version
     */
    private String version;
    @Transient
    private byte[] versionBytes;
    /**
     * risk management data
     */
    private String riskManageData;
    @Transient
    private byte[] riskManageDataBytes;
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
    //tag 9F35 Indicates terminal's communication capability, and its operational control
    private String terminalType;
    @Transient
    private byte terminalTypeByte;
    //Read the IC card PIN retry counter before verify the PIN or not (1 : read(default), 0 : not read)
    private byte getDataPIN;
    //0-Not supported,1-Supported(default)
    private byte bypassPin;
    //whether bypass all other pin when one pin has been bypassed 1-Yes,0-No
    private byte bypassAllFlg;
    //Merchant force online (1 means always require online transaction)
    private byte forceOnline;

    @Generated(hash = 1985344347)
    public EmvAid(Long id, @NotNull String appName, @NotNull String aid, int selFlag, int priority,
            boolean onlinePin, int targetPer, int maxTargetPer, int floorLimitCheckFlg, boolean randTransSel,
            boolean velocityCheck, long floorLimit, long threshold, String tacDenial, String tacOnline,
            String tacDefault, String acquirerId, String dDOL, String tDOL, String version, String riskManageData,
            String terminalCapability, String terminalAdditionalCapability, String terminalType, byte getDataPIN,
            byte bypassPin, byte bypassAllFlg, byte forceOnline) {
        this.id = id;
        this.appName = appName;
        this.aid = aid;
        this.selFlag = selFlag;
        this.priority = priority;
        this.onlinePin = onlinePin;
        this.targetPer = targetPer;
        this.maxTargetPer = maxTargetPer;
        this.floorLimitCheckFlg = floorLimitCheckFlg;
        this.randTransSel = randTransSel;
        this.velocityCheck = velocityCheck;
        this.floorLimit = floorLimit;
        this.threshold = threshold;
        this.tacDenial = tacDenial;
        this.tacOnline = tacOnline;
        this.tacDefault = tacDefault;
        this.acquirerId = acquirerId;
        this.dDOL = dDOL;
        this.tDOL = tDOL;
        this.version = version;
        this.riskManageData = riskManageData;
        this.terminalCapability = terminalCapability;
        this.terminalAdditionalCapability = terminalAdditionalCapability;
        this.terminalType = terminalType;
        this.getDataPIN = getDataPIN;
        this.bypassPin = bypassPin;
        this.bypassAllFlg = bypassAllFlg;
        this.forceOnline = forceOnline;
    }

    @Generated(hash = 1782441615)
    public EmvAid() {
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

    public int getSelFlag() {
        return selFlag;
    }

    public void setSelFlag(int selFlag) {
        this.selFlag = selFlag;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isOnlinePin() {
        return onlinePin;
    }

    public void setOnlinePin(boolean onlinePin) {
        this.onlinePin = onlinePin;
    }

    public int getTargetPer() {
        return targetPer;
    }

    public void setTargetPer(int targetPer) {
        this.targetPer = targetPer;
    }

    public int getMaxTargetPer() {
        return maxTargetPer;
    }

    public void setMaxTargetPer(int maxTargetPer) {
        this.maxTargetPer = maxTargetPer;
    }

    public int getFloorLimitCheckFlg() {
        return floorLimitCheckFlg;
    }

    public void setFloorLimitCheckFlg(int floorLimitCheckFlg) {
        this.floorLimitCheckFlg = floorLimitCheckFlg;
    }

    public boolean isRandTransSel() {
        return randTransSel;
    }

    public void setRandTransSel(boolean randTransSel) {
        this.randTransSel = randTransSel;
    }

    public byte getRandTransSelByte() {
        return (byte) (randTransSel?1:0);
    }

    public void setRandTransSelByte(byte randTransSelByte) {
        this.randTransSelByte = randTransSelByte;
    }

    public boolean isVelocityCheck() {
        return velocityCheck;
    }

    public void setVelocityCheck(boolean velocityCheck) {
        this.velocityCheck = velocityCheck;
    }

    public byte getVelocityCheckByte() {
        return (byte) (velocityCheck?1:0);
    }

    public void setVelocityCheckByte(byte velocityCheckByte) {
        this.velocityCheckByte = velocityCheckByte;
    }

    public long getFloorLimit() {
        return floorLimit;
    }

    public void setFloorLimit(long floorLimit) {
        this.floorLimit = floorLimit;
    }

    public long getThreshold() {
        return threshold;
    }

    public void setThreshold(long threshold) {
        this.threshold = threshold;
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

    public byte getGetDataPIN() {
        return getDataPIN;
    }

    public void setGetDataPIN(byte getDataPIN) {
        this.getDataPIN = getDataPIN;
    }

    public byte getBypassPin() {
        return bypassPin;
    }

    public void setBypassPin(byte bypassPin) {
        this.bypassPin = bypassPin;
    }

    public byte getBypassAllFlg() {
        return bypassAllFlg;
    }

    public void setBypassAllFlg(byte bypassAllFlg) {
        this.bypassAllFlg = bypassAllFlg;
    }

    public byte getForceOnline() {
        return forceOnline;
    }

    public void setForceOnline(byte forceOnline) {
        this.forceOnline = forceOnline;
    }

    @Override
    public String toString() {
        return appName;
    }

    public boolean getOnlinePin() {
        return this.onlinePin;
    }

    public boolean getRandTransSel() {
        return this.randTransSel;
    }

    public boolean getVelocityCheck() {
        return this.velocityCheck;
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
