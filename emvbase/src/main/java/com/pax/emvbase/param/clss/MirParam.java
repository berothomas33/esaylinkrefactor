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
 * 2021/06/15                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

import androidx.annotation.NonNull;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.param.common.Config;

/**
 * MIR Kernel Param
 */
public class MirParam extends BaseParam<MirAid> {
    // From Aid
    private byte[] tacDefault;
    private byte[] tacDenial;
    private byte[] tacOnline;
    private byte[] floorLimitBytes;
    private byte[] transLimitBytes;
    private byte[] cvmLimitBytes;

    // From Config
    private byte[] termId;

    // From EmvTransParam / Clss_TransParam
    private byte[] transNo;

    // API param
    private byte exceptFileFlag;

    // TLV param
    private byte[] refundTacDenial;
    private byte[] refundFloorLimit;
    private byte[] termTPMCapability;
    private byte[] tlvDF810CTag;
    private byte[] transRecoveryLimit;
    private byte[] dataExchangeTagList;
    private byte[] protocol2Tag82;

    public MirParam() {
        // You can set default values for some fields here
        termType = 0x22;
        tlvDF810CTag = new byte[]{(byte) 0x81};
        merchantCategoryCode = new byte[] {0x00, 0x01};
        transCurrExp = 0x02;
        refundTacDenial = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        termTPMCapability = new byte[]{(byte) 0xA8, 0x00};
        transRecoveryLimit = new byte[]{0x03};
        dataExchangeTagList = new byte[]{0x5A};
        protocol2Tag82 = new byte[]{0x00, 0x00};
        exceptFileFlag = 0;
    }

    @NonNull
    @Override
    public MirParam loadFromAid(MirAid aid) {
        appVersion = aid.getVersion();
        tacDefault = aid.getTacDefault();
        tacDenial = aid.getTacDenial();
        tacOnline = aid.getTacOnline();
        acquirerId = aid.getAcquirerId();
        floorLimitBytes = aid.getFloorLimitBytes();
        transLimitBytes = aid.getTransLimitBytes();
        cvmLimitBytes = aid.getCvmLimitBytes();
        termType = aid.getTermType();
        //termCapability = aid.getTermCapability();
        refundFloorLimit = aid.getRefundFloorLimit();
        refundTacDenial = aid.getRefundTacDenial();
        termTPMCapability = aid.getTermTPMCapability();
        tlvDF810CTag = aid.getTlvDF810CTag();
        transRecoveryLimit = aid.getTransRecoveryLimit();
        dataExchangeTagList = aid.getDataExchangeTagList();
        protocol2Tag82 = aid.getProtocol2Tag82();
        exceptFileFlag = aid.getExceptFileFlag();
        return this;
    }

    @NonNull
    @Override
    public MirParam loadFromConfig(Config config) {
        countryCode = config.getTerminalCountryCode();
        merchantId = config.getMerchantId();
        merchantNameLocation = config.getMerchantNameAndLocationBytes();
        merchantCategoryCode = config.getMerchantCategoryCode();
        return this;
    }

    @NonNull
    @Override
    public MirParam loadFromEmvTransParam(EmvTransParam param) {
        termId = param.getTerminalID();
        transType = param.getTransType();
        transCurrCode = param.getTransCurrencyCode();
        transCurrExp = param.getTransCurrencyExponent();
        transDate = param.getTransDate();
        transTime = param.getTransTime();
        amount = param.getAmountBytes();
        otherAmount = param.getAmountOtherBytes();
        transNo = param.getTransTraceNoBytes();
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

    public byte[] getFloorLimitBytes() {
        return floorLimitBytes;
    }

    public byte[] getTransLimitBytes() {
        return transLimitBytes;
    }

    public byte[] getCvmLimitBytes() {
        return cvmLimitBytes;
    }

    public byte[] getTransNo() {
        return transNo;
    }

    public byte[] getRefundTacDenial() {
        return refundTacDenial;
    }

    public byte[] getTermTPMCapability() {
        return termTPMCapability;
    }

    public byte[] getTransRecoveryLimit() {
        return transRecoveryLimit;
    }

    public byte[] getDataExchangeTagList() {
        return dataExchangeTagList;
    }

    public byte[] getProtocol2Tag82() {
        return protocol2Tag82;
    }

    public byte[] getTlvDF810CTag() {
        return tlvDF810CTag;
    }

    public byte[] getTermId() {
        return termId;
    }

    public byte getExceptFileFlag() {
        return exceptFileFlag;
    }

    public byte[] getRefundFloorLimit() {
        return refundFloorLimit;
    }
}
