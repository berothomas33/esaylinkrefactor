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
 * 2021/06/16                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

import androidx.annotation.NonNull;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.param.common.Config;

public class PayPassParam extends BaseParam<PayPassAid> {

    // From Aid
    private byte[] tacDefault;
    private byte[] tacDenial;
    private byte[] tacOnline;
    private byte[] kernelConfig;
    private byte[] termRiskManage;
    private byte[] cardDataInput;
    private byte[] cvmRequired;
    private byte[] noCvmRequired;
    private byte[] securityCapability;
    private byte[] magVersion;
    private byte[] magCvm;
    private byte[] magNoCvm;
    private byte[] kernelId;
    private byte[] addCapability;
    private byte dataExchangeSupportFlag;
    private byte[] tlvParam;
    private byte[] defaultUDOL;
    private byte[] refundVoidFloorLimit;
    private byte[] refundVoidTacDenial;
    private byte[] cvmTransLimit;
    private byte[] floorLimitBytes;
    private byte[] transLimitBytes;
    private byte[] cvmLimitBytes;
    private boolean supportDefaultMcTermParam;
    private byte[] maxTornNum;
    private byte[] maxTornLifetime;
    private byte[] deviceSN;
    private byte[] dsOperatorId;

    public PayPassParam() {
        dataExchangeSupportFlag = 0x01;
        tlvParam = new byte[]{0x01, 0x01, 0x04};
        defaultUDOL = new byte[]{(byte) 0x9F, 0x6A, 0x04};
        refundVoidFloorLimit = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        refundVoidTacDenial = new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
                (byte)0xFF};
        supportDefaultMcTermParam = true;
    }

    @NonNull
    @Override
    public PayPassParam loadFromAid(PayPassAid aid) {
        appVersion = aid.getVersion();
        kernelConfig = aid.getKernelConfig();
        tacDefault = aid.getTacDefault();
        tacOnline = aid.getTacOnline();
        tacDenial = aid.getTacDenial();
        floorLimitBytes = aid.getFloorLimitBytes();
        transLimitBytes = aid.getTransLimitBytes();
        cvmTransLimit = aid.getCvmTransLimitBytes();
        cvmLimitBytes = aid.getCvmLimitBytes();
        termRiskManage = aid.getTermRiskManage();
        cardDataInput = aid.getCardDataInput();
        cvmRequired = aid.getCvmRequired();
        noCvmRequired = aid.getNoCvmRequired();
        securityCapability = aid.getSecurityCapability();
        magVersion = aid.getMagVersion();
        magCvm = aid.getMagCvm();
        magNoCvm = aid.getMagNoCvm();
        kernelId = aid.getKernelId();
        addCapability = aid.getAddCapability();
        termType = aid.getTermType();
        dataExchangeSupportFlag = aid.getDataExchangeSupportFlag();
        tlvParam = aid.getTlvParam();
        defaultUDOL = aid.getDefaultUDOL();
        refundVoidFloorLimit = aid.getRefundVoidFloorLimit();
        refundVoidTacDenial = aid.getRefundVoidTacDenial();
        supportDefaultMcTermParam = aid.isSupportDefaultMcTermParam();
        maxTornNum = aid.getMaxTornNum();
        maxTornLifetime = aid.getMaxTornLifetime();
        deviceSN = aid.getDeviceSN();
        dsOperatorId = aid.getDsOperatorId();
        return this;
    }

    @NonNull
    @Override
    public PayPassParam loadFromConfig(Config config) {
        merchantId = config.getMerchantId();
        merchantCategoryCode = config.getMerchantCategoryCode();
        merchantNameLocation = config.getMerchantNameAndLocationBytes();
        countryCode = config.getTerminalCountryCode();
        referCurrCode = config.getTransReferenceCurrencyCode();
        return this;
    }

    @NonNull
    @Override
    public PayPassParam loadFromEmvTransParam(EmvTransParam param) {
        transDate = param.getTransDate();
        transTime = param.getTransTime();
        transType = param.getTransType();
        amount = param.getAmountBytes();
        otherAmount = param.getAmountOtherBytes();
        return this;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Getter and Setter /////////////////////////////////////////////////////////////////

    public byte[] getTacDefault() {
        return tacDefault;
    }

    public byte[] getTacDenial() {
        return tacDenial;
    }

    public byte[] getTacOnline() {
        return tacOnline;
    }

    public byte getDataExchangeSupportFlag() {
        return dataExchangeSupportFlag;
    }

    public byte[] getTlvParam() {
        return tlvParam;
    }

    public byte[] getDefaultUDOL() {
        return defaultUDOL;
    }

    public byte[] getRefundVoidFloorLimit() {
        return refundVoidFloorLimit;
    }

    public byte[] getRefundVoidTacDenial() {
        return refundVoidTacDenial;
    }

    public byte[] getKernelConfig() {
        return kernelConfig;
    }

    public byte[] getTermRiskManage() {
        return termRiskManage;
    }

    public byte[] getCardDataInput() {
        return cardDataInput;
    }

    public byte[] getCvmRequired() {
        return cvmRequired;
    }

    public byte[] getNoCvmRequired() {
        return noCvmRequired;
    }

    public byte[] getSecurityCapability() {
        return securityCapability;
    }

    public byte[] getMagVersion() {
        return magVersion;
    }

    public byte[] getMagCvm() {
        return magCvm;
    }

    public byte[] getMagNoCvm() {
        return magNoCvm;
    }

    public byte[] getKernelId() {
        return kernelId;
    }

    public byte[] getAddCapability() {
        return addCapability;
    }

    public byte[] getCvmTransLimit() {
        return cvmTransLimit;
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

    public boolean isSupportDefaultMcTermParam() {
        return supportDefaultMcTermParam;
    }

    public byte[] getMaxTornNum() {
        return maxTornNum;
    }

    public byte[] getMaxTornLifetime() {
        return maxTornLifetime;
    }

    public byte[] getDeviceSN() {
        return deviceSN;
    }

    public byte[] getDsOperatorId() {
        return dsOperatorId;
    }
}
