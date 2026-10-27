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
 * 2021/06/18                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

import androidx.annotation.NonNull;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.param.common.Config;

/**
 * RuPay Kernel Param
 */
public class RuPayParam extends BaseParam<RuPayAid> {
    // From Aid
    private byte[] tacDefault;
    private byte[] tacDenial;
    private byte[] tacOnline;
    private byte[] termCapability;
    private byte[] floorLimitBytes;
    private byte[] transLimitBytes;
    private byte[] cvmLimitBytes;

    // From EmvTransParam
    private byte[] termId;

    // TLV Param
    private byte[] addCapability;
    private byte[] serviceIdBytes;

    public RuPayParam() {
        // You can set default values for some fields here
        acquirerId = new byte[]{0x00, 0x00, 0x00, 0x12, 0x34, 0x56};
        addCapability = new byte[]{(byte) 0xF0, (byte) 0x00, (byte) 0xF0, (byte) 0xA0, (byte) 0x01};
        merchantCategoryCode = new byte[]{0x00, 0x01};
        merchantNameLocation = new byte[]{0x00};
        countryCode = new byte[]{0x03, 0x56};
        termCapability = new byte[]{(byte) 0xE0, (byte) 0xE1, (byte) 0xC8};
        termType = 0x22;
        transCurrCode = new byte[]{0x03, 0x56};
        transCurrExp = 0x02;
        serviceIdBytes = new byte[]{0x00, 0x00};
    }

    @NonNull
    @Override
    public RuPayParam loadFromAid(RuPayAid aid) {
        appVersion = aid.getVersion();
        tacDefault = aid.getTacDefault();
        tacDenial = aid.getTacDenial();
        tacOnline = aid.getTacOnline();
        acquirerId = aid.getAcquirerId();
        floorLimitBytes = aid.getFloorLimitBytes();
        transLimitBytes = aid.getTransLimitBytes();
        cvmLimitBytes = aid.getCvmLimitBytes();
        termType = aid.getTermType();
        termCapability = aid.getTermCapability();
        addCapability = aid.getAddCapability();
        serviceIdBytes = aid.getServiceIdBytes();
        return this;
    }

    @NonNull
    @Override
    public RuPayParam loadFromConfig(Config config) {
        // RuPay country code MUST BE 0356 (India)
        //countryCode = config.getTerminalCountryCode();
        merchantNameLocation = config.getMerchantNameAndLocationBytes();
        merchantCategoryCode = config.getMerchantCategoryCode();
        return this;
    }

    @NonNull
    @Override
    public RuPayParam loadFromEmvTransParam(EmvTransParam param) {
        termId = param.getTerminalID();
        transType = param.getTransType();
        // RuPay currency code MUST BE 0356 (India)
        //transCurrCode = param.getTransCurrencyCode();
        transCurrExp = param.getTransCurrencyExponent();
        transDate = param.getTransDate();
        transTime = param.getTransTime();
        amount = param.getAmountBytes();
        otherAmount = param.getAmountOtherBytes();
        return this;
    }

    // Getter and Setter

    public byte[] getTacDefault() {
        return tacDefault;
    }

    public byte[] getTacDenial() {
        return tacDenial;
    }

    public byte[] getTacOnline() {
        return tacOnline;
    }

    public byte[] getTermCapability() {
        return termCapability;
    }

    public byte[] getAddCapability() {
        return addCapability;
    }

    public byte[] getFloorLimitBytes() {
        return floorLimitBytes;
    }

    public byte[] getTransLimitBytes() {
        return transLimitBytes;
    }

    public byte[] getCvmLimitBytes() {
        return cvmLimitBytes;
    }

    public byte[] getTermId() {
        return termId;
    }

    public byte[] getServiceIdBytes() {
        return serviceIdBytes;
    }
}
