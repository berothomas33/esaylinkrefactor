/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2021-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                           Author                      Action
 * 2021/06/17                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

import androidx.annotation.NonNull;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.param.common.Config;

/**
 * PBOC Kernel Param
 */
public class PbocParam extends BaseParam<PbocAid> {
    // From Aid
    private String aidValue;
    private long trueFloorLimit;
    private long trueCvmLimit;//long
    private byte[] termCapability;
    private byte[] exTermCapability;
    private long qpsLimit;

    // From Config
    private byte referCurrExp;
    private short merchantNameLocLen;

    // From EmvTransParam / Clss_TransParam
    private long trueAmount;
    private byte[] termId;

    // API param
    private byte quicsFlag;
    private long tornMaxLifeTime;
    private short tornLogMaxNum;
    private short tornSupport;
    private byte[] tornRFU;
    private byte[] aucRFU;
    private byte aidType;

    public PbocParam() {
        quicsFlag = 2;
        tornMaxLifeTime = 300;
        tornLogMaxNum = 3;
        tornSupport = 1;
        tornRFU = new byte[4];
        aucRFU = new byte[4];
        aidType = 0;
    }

    @NonNull
    @Override
    public PbocParam loadFromAid(PbocAid aid) {
        aidValue = aid.getAidValue();
        acquirerId = aid.getAcquirerId();
        trueFloorLimit = aid.getFloorLimit();
        trueCvmLimit = aid.getCvmLimit();
        termType = aid.getTermType();
        termCapability = aid.getTermCapability();
        exTermCapability = aid.getTermAddCapability();
        quicsFlag = aid.getQuicsFlag();
        tornMaxLifeTime = aid.getTornMaxLifeTime();
        tornLogMaxNum = aid.getTornLogMaxNum();
        tornSupport = aid.getTornSupport();
        aidType = aid.getAidType();
        tornRFU = aid.getTornRFU();
        aucRFU = aid.getAucRFU();
        qpsLimit = aid.getQpsLimit();
        return this;
    }

    @NonNull
    @Override
    public PbocParam loadFromConfig(Config config) {
        merchantCategoryCode = config.getMerchantCategoryCode();
        merchantNameLocation = config.getMerchantNameAndLocationBytes();
        merchantNameLocLen = (short) config.getMerchantNameAndLocation().length();
        referCurrCon = config.getConversionRatio();
        referCurrCode = config.getTransReferenceCurrencyCode();
        referCurrExp = config.getTransReferenceCurrencyExponent();
        merchantId = config.getMerchantId();
        countryCode = config.getTerminalCountryCode();
        return this;
    }

    @NonNull
    @Override
    public PbocParam loadFromEmvTransParam(EmvTransParam param) {
        termId = param.getTerminalID();
        trueAmount = param.getAmount();
        transCurrCode = param.getTransCurrencyCode();
        return this;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Getter and Setter /////////////////////////////////////////////////////////////////

    public byte[] getTermCapability() {
        return termCapability;
    }

    public String getAidValue() {
        return aidValue;
    }

    public long getTrueFloorLimit() {
        return trueFloorLimit;
    }

    public long getTrueCvmLimit() {
        return trueCvmLimit;
    }

    public byte getReferCurrExp() {
        return referCurrExp;
    }

    public byte[] getTermId() {
        return termId;
    }

    public byte[] getExTermCapability() {
        return exTermCapability;
    }

    public short getMerchantNameLocLen() {
        return merchantNameLocLen;
    }

    public byte getQuicsFlag() {
        return quicsFlag;
    }

    public long getTornMaxLifeTime() {
        return tornMaxLifeTime;
    }

    public short getTornLogMaxNum() {
        return tornLogMaxNum;
    }

    public short getTornSupport() {
        return tornSupport;
    }

    public byte[] getTornRFU() {
        return tornRFU;
    }

    public byte[] getAucRFU() {
        return aucRFU;
    }

    public long getTrueAmount() {
        return trueAmount;
    }

    public byte getAidType() {
        return aidType;
    }

    public long getQpsLimit() {
        return qpsLimit;
    }
}
