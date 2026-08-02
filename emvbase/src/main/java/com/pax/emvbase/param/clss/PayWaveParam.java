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
 * 2021/06/21                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

import androidx.annotation.NonNull;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.param.common.Config;
import java.util.List;

/**
 * PayWave Kernel Param
 */
public class PayWaveParam extends BaseParam<PayWaveAid> {

    // From AID
    private byte[] TTQ;
    private byte securityCapability;
    private List<PayWaveInterFloorLimit> interFloorLimitList;

    // From Config
    private short merchantNameLocationLen;
    private byte referCurrExp;

    // From EmvTransParam
    private byte[] termId;

    // API Param
    private byte domesticOnly;
    private byte enDDAVerNo;

    // Other
    private List<PayWaveProgramId> waveProgramIdList;

    public PayWaveParam() {
        // You can set default values for some fields here
        domesticOnly = 0x00;
        enDDAVerNo = 0x00;
    }

    @NonNull
    @Override
    public PayWaveParam loadFromAid(PayWaveAid aid) {
        termType = aid.getTermType();
        TTQ = aid.getTTQ();
        securityCapability = aid.getSecurityCapability();
        interFloorLimitList = aid.getPayWaveInterFloorLimitList();
        domesticOnly = aid.getDomesticOnly();
        enDDAVerNo = aid.getEnDDAVerNo();
        return this;
    }

    @NonNull
    @Override
    public PayWaveParam loadFromConfig(Config config) {
        referCurrCon = config.getConversionRatio();
        referCurrCode = config.getTransReferenceCurrencyCode();
        referCurrExp = config.getTransReferenceCurrencyExponent();
        countryCode = config.getTerminalCountryCode();
        merchantId = config.getMerchantId();
        merchantCategoryCode = config.getMerchantCategoryCode();
        merchantNameLocation = config.getMerchantNameAndLocationBytes();
        merchantNameLocationLen = (short) config.getMerchantNameAndLocation().length();
        return this;
    }

    @NonNull
    @Override
    public PayWaveParam loadFromEmvTransParam(EmvTransParam param) {
        termId = param.getTerminalID();
        transType = param.getTransType();
        transCurrCode = param.getTransCurrencyCode();
        transCurrExp = param.getTransCurrencyExponent();
        return this;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Getter and Setter /////////////////////////////////////////////////////////////////

    public short getMerchantNameLocationLen() {
        return merchantNameLocationLen;
    }

    public List<PayWaveProgramId> getWaveProgramIdList() {
        return waveProgramIdList;
    }

    public void setWaveProgramIdList(
            List<PayWaveProgramId> waveProgramIdList) {
        this.waveProgramIdList = waveProgramIdList;
    }

    public byte getReferCurrExp() {
        return referCurrExp;
    }

    public byte[] getTermId() {
        return termId;
    }

    public byte[] getTTQ() {
        return TTQ;
    }

    public byte getSecurityCapability() {
        return securityCapability;
    }

    public List<PayWaveInterFloorLimit> getInterFloorLimitList() {
        return interFloorLimitList;
    }

    public byte getDomesticOnly() {
        return domesticOnly;
    }

    public byte getEnDDAVerNo() {
        return enDDAVerNo;
    }
}
