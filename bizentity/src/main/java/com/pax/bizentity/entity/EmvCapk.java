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

import com.pax.commonlib.utils.ConvertUtils;
import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Transient;

@Entity(nameInDb = "capk")
public class EmvCapk implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String ID_FIELD_NAME = "id";
    public static final String RID_FIELD_NAME = "rid";

    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    private Long id;

    // rID
    @Property(nameInDb = RID_FIELD_NAME)
    @NotNull
    private String rID;
    @Transient
    private byte[] rIDBytes;
    // key index
    @NotNull
    private int keyID;
    // HASH algo index
    @NotNull
    private int hashInd;
    // RSA algo index
    @NotNull
    private int arithInd;
    // module
    private String module;
    @Transient
    private byte[] moduleBytes;
    // exponent
    private String exponent;
    @Transient
    private byte[] exponentBytes;
    // exp date(YYMMDD)
    private String expDate;
    @Transient
    private byte[] expDateBytes;
    // check sum
    private String checkSum;
    @Transient
    private byte[] checkSumBytes;

    @Generated(hash = 1292160809)
    public EmvCapk(Long id, @NotNull String rID, int keyID, int hashInd, int arithInd,
            String module, String exponent, String expDate, String checkSum) {
        this.id = id;
        this.rID = rID;
        this.keyID = keyID;
        this.hashInd = hashInd;
        this.arithInd = arithInd;
        this.module = module;
        this.exponent = exponent;
        this.expDate = expDate;
        this.checkSum = checkSum;
    }

    @Generated(hash = 712986926)
    public EmvCapk() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRID() {
        return rID;
    }

    public void setRID(String rID) {
        this.rID = rID;
    }

    public byte[] getrIDBytes() {
        return ConvertUtils.strToBcdPaddingLeft(rID);
    }

    public void setrIDBytes(byte[] rIDBytes) {
        this.rIDBytes = rIDBytes;
    }

    public int getKeyID() {
        return keyID;
    }

    public void setKeyID(int keyID) {
        this.keyID = keyID;
    }

    public int getHashInd() {
        return hashInd;
    }

    public void setHashInd(int hashInd) {
        this.hashInd = hashInd;
    }

    public int getArithInd() {
        return arithInd;
    }

    public void setArithInd(int arithInd) {
        this.arithInd = arithInd;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public byte[] getModuleBytes() {
        return ConvertUtils.strToBcdPaddingLeft(module);
    }

    public void setModuleBytes(byte[] moduleBytes) {
        this.moduleBytes = moduleBytes;
    }

    public String getExponent() {
        return exponent;
    }

    public void setExponent(String exponent) {
        this.exponent = exponent;
    }

    public byte[] getExponentBytes() {
        return ConvertUtils.strToBcdPaddingLeft(exponent);
    }

    public void setExponentBytes(byte[] exponentBytes) {
        this.exponentBytes = exponentBytes;
    }

    public String getExpDate() {
        return expDate;
    }

    public void setExpDate(String expDate) {
        this.expDate = expDate;
    }

    public byte[] getExpDateBytes() {
        return ConvertUtils.strToBcdPaddingLeft(expDate);
    }

    public void setExpDateBytes(byte[] expDateBytes) {
        this.expDateBytes = expDateBytes;
    }

    public String getCheckSum() {
        return checkSum;
    }

    public void setCheckSum(String checkSum) {
        this.checkSum = checkSum;
    }

    public byte[] getCheckSumBytes() {
        return ConvertUtils.strToBcdPaddingLeft(checkSum);
    }

    public void setCheckSumBytes(byte[] checkSumBytes) {
        this.checkSumBytes = checkSumBytes;
    }

    @Override
    public String toString() {
        return rID;
    }
}
