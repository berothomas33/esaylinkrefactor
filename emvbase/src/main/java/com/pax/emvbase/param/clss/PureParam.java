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
 * Pure Kernel Param
 */
public class PureParam extends BaseParam<PureAid> {
    // From Aid
    private byte[] tacDefault;
    private byte[] tacDenial;
    private byte[] tacOnline;
    private byte[] termCapability;

    // TLV param
    private byte[] addTagObjList;
    private byte[] mandatoryTagObjList;
    private byte[] authTransDataTagObjList;
    private byte[] clssKernelCapability;
    private byte[] clssPOSImplOption;
    private byte[] appAuthTransType;
    private byte[] defaultDDOL;
    private byte[] refundTacDenial;
    private byte[] addCapability;
    private byte[] timeout;
    private byte[] memorySlotReadTemp;
    private byte[] memorySlotUpdateTemp;

    // Other
    private byte impOption;
    private byte[] checkExceptionFilePan;

    public PureParam() {
        // You can set default values for some fields here
        addTagObjList = new byte[]{(byte)0x9F, (byte)0x02, (byte)0x9F, (byte)0x03, (byte)0x9F,
                (byte)0x26, (byte)0x82, (byte)0x9F, (byte)0x36, (byte)0x9F, (byte)0x27, (byte)0x9F,
                (byte)0x10, (byte)0x9F, (byte)0x1A, (byte)0x95, (byte)0x5F, (byte)0x2A, (byte)0x9A,
                (byte)0x9C, (byte)0x9F, (byte)0x37, (byte)0x9F, (byte)0x35, (byte)0x57, (byte)0x9F,
                (byte)0x34, (byte)0x84, (byte)0x5F, (byte)0x34, (byte)0x5A, (byte)0xC7, (byte)0x9F,
                (byte)0x33, (byte)0x9F, (byte)0x73, (byte)0x9F, (byte)0x77, (byte)0x9F, (byte)0x45};
        mandatoryTagObjList = new byte[]{(byte) 0x8C, 0x57};
        authTransDataTagObjList = new byte[]{(byte) 0x82, (byte) 0x95, (byte) 0x9F, (byte) 0x77,
                (byte) 0x84};
        clssKernelCapability = new byte[]{0x36, 0x00, 0x40, 0x43, (byte) 0xF9};
        clssPOSImplOption = new byte[]{(byte) 0xF8};
        acquirerId = new byte[]{0x00, 0x00, 0x00, 0x12, 0x34, 0x56};
        termCapability = new byte[]{(byte) 0xE0, (byte) 0x68, (byte) 0x48};
        termType = 0x22;
        merchantCategoryCode = new byte[]{0x00, 0x00};
        merchantNameLocation = new byte[]{0x46, 0x49, 0x4D, 0x45};
        appAuthTransType = new byte[]{(byte) 0x90};
        defaultDDOL = new byte[]{(byte) 0x9F, 0x37, 0x04};
        refundTacDenial = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF};
        addCapability = new byte[]{(byte) 0xF0, (byte) 0x00, (byte) 0xF0, (byte) 0xA0, (byte) 0x01};
        timeout = new byte[]{0x10, 0x00};
        impOption = (byte) 0xF8;
        transCurrCode = new byte[]{0x07, 0x04};
        countryCode = new byte[]{0x07, 0x04};
        memorySlotReadTemp = new byte[]{(byte)0xBF, (byte)0x71, (byte)0x06, (byte)0x9F, (byte)0x70,
                (byte)0x03, (byte)0xDF, (byte)0x01, (byte)0x02};
        memorySlotUpdateTemp = new byte[]{(byte)0xBF, (byte)0x70, (byte)0x1E, (byte)0xA2,
                (byte)0x07, (byte)0xDF, (byte)0x01, (byte)0x01, (byte)0x11, (byte)0x85, (byte)0x01,
                (byte)0x00, (byte)0xA2, (byte)0x08, (byte)0xDF, (byte)0x02, (byte)0x02, (byte)0x12,
                (byte)0x34, (byte)0x85, (byte)0x01, (byte)0x01, (byte)0xA2, (byte)0x09, (byte)0xDF,
                (byte)0x03, (byte)0x03, (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x85, (byte)0x01,
                (byte)0x01};
        checkExceptionFilePan = new byte[]{0x22, 0x22, (byte) 0x99, (byte) 0x99, (byte) 0x99,
                (byte) 0x99, (byte) 0x99, (byte) 0x90, (byte) 0xFF, (byte) 0xFF};
    }

    @NonNull
    public PureParam loadFromAid(PureAid aid) {
        appVersion = aid.getVersion();
        acquirerId = aid.getAcquirerId();
        tacDefault = aid.getTacDefault();
        tacDenial = aid.getTacDenial();
        tacOnline = aid.getTacOnline();
        addCapability = aid.getAddCapability();
        termType = aid.getTermType();
        termCapability = aid.getTermCapability();
        addTagObjList = aid.getAddTagObjList();
        mandatoryTagObjList = aid.getMandatoryTagObjList();
        authTransDataTagObjList = aid.getAuthTransDataTagObjList();
        clssKernelCapability = aid.getClssKernelCapability();
        clssPOSImplOption = aid.getClssPOSImplOption();
        appAuthTransType = aid.getAppAuthTransType();
        defaultDDOL = aid.getDefaultDDOL();
        refundTacDenial = aid.getRefundTacDenial();
        timeout = aid.getTimeout();
        memorySlotReadTemp = aid.getMemorySlotReadTemp();
        memorySlotUpdateTemp = aid.getMemorySlotUpdateTemp();
        impOption = aid.getImpOption();
        checkExceptionFilePan = aid.getCheckExceptionFilePan();
        return this;
    }

    @Override
    @NonNull
    public PureParam loadFromConfig(Config config) {
        merchantCategoryCode = config.getMerchantCategoryCode();
        merchantNameLocation = config.getMerchantNameAndLocationBytes();
        countryCode = config.getTerminalCountryCode();
        return this;
    }

    @Override
    @NonNull
    public PureParam loadFromEmvTransParam(EmvTransParam param) {
        transType = param.getTransType();
        transDate = param.getTransDate();
        transCurrCode = param.getTransCurrencyCode();
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

    public byte[] getTermCapability() {
        return termCapability;
    }

    public byte[] getAddTagObjList() {
        return addTagObjList;
    }

    public byte[] getMandatoryTagObjList() {
        return mandatoryTagObjList;
    }

    public byte[] getClssKernelCapability() {
        return clssKernelCapability;
    }

    public byte[] getClssPOSImplOption() {
        return clssPOSImplOption;
    }

    public byte[] getAppAuthTransType() {
        return appAuthTransType;
    }

    public byte[] getDefaultDDOL() {
        return defaultDDOL;
    }

    public byte[] getAddCapability() {
        return addCapability;
    }

    public byte[] getTimeout() {
        return timeout;
    }

    public byte[] getCheckExceptionFilePan() {
        return checkExceptionFilePan;
    }

    public byte[] getAuthTransDataTagObjList() {
        return authTransDataTagObjList;
    }

    public byte[] getRefundTacDenial() {
        return refundTacDenial;
    }

    public byte getImpOption() {
        return impOption;
    }

    public byte[] getMemorySlotReadTemp() {
        return memorySlotReadTemp;
    }

    public byte[] getMemorySlotUpdateTemp() {
        return memorySlotUpdateTemp;
    }
}
