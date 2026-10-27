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
 * 2021/06/22                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

/**
 * Pure Kernel AID
 */
public class PureAid extends BaseAid {
    // Terminal Action Code – Denial
    private byte[] tacDenial;

    // Terminal Action Code – Online
    private byte[] tacOnline;

    // Terminal Action Code – Default
    private byte[] tacDefault;
    private byte[] termCapability;
    private byte[] addCapability;
    private byte[] addTagObjList;
    private byte[] mandatoryTagObjList;
    private byte[] authTransDataTagObjList;
    private byte[] clssKernelCapability;
    private byte[] clssPOSImplOption;
    private byte[] appAuthTransType;
    private byte[] defaultDDOL;
    private byte[] refundTacDenial;
    private byte[] timeout;
    private byte[] memorySlotReadTemp;
    private byte[] memorySlotUpdateTemp;
    private byte impOption;
    private byte[] checkExceptionFilePan;

    public byte[] getTacDenial() {
        return tacDenial;
    }

    public void setTacDenial(byte[] tacDenial) {
        this.tacDenial = tacDenial;
    }

    public byte[] getTacOnline() {
        return tacOnline;
    }

    public void setTacOnline(byte[] tacOnline) {
        this.tacOnline = tacOnline;
    }

    public byte[] getTacDefault() {
        return tacDefault;
    }

    public void setTacDefault(byte[] tacDefault) {
        this.tacDefault = tacDefault;
    }

    public byte[] getTermCapability() {
        return termCapability;
    }

    public void setTermCapability(byte[] termCapability) {
        this.termCapability = termCapability;
    }

    public byte[] getAddCapability() {
        return addCapability;
    }

    public void setAddCapability(byte[] addCapability) {
        this.addCapability = addCapability;
    }

    public byte[] getAddTagObjList() {
        return addTagObjList;
    }

    public void setAddTagObjList(byte[] addTagObjList) {
        this.addTagObjList = addTagObjList;
    }

    public byte[] getMandatoryTagObjList() {
        return mandatoryTagObjList;
    }

    public void setMandatoryTagObjList(byte[] mandatoryTagObjList) {
        this.mandatoryTagObjList = mandatoryTagObjList;
    }

    public byte[] getAuthTransDataTagObjList() {
        return authTransDataTagObjList;
    }

    public void setAuthTransDataTagObjList(byte[] authTransDataTagObjList) {
        this.authTransDataTagObjList = authTransDataTagObjList;
    }

    public byte[] getClssKernelCapability() {
        return clssKernelCapability;
    }

    public void setClssKernelCapability(byte[] clssKernelCapability) {
        this.clssKernelCapability = clssKernelCapability;
    }

    public byte[] getClssPOSImplOption() {
        return clssPOSImplOption;
    }

    public void setClssPOSImplOption(byte[] clssPOSImplOption) {
        this.clssPOSImplOption = clssPOSImplOption;
    }

    public byte[] getAppAuthTransType() {
        return appAuthTransType;
    }

    public void setAppAuthTransType(byte[] appAuthTransType) {
        this.appAuthTransType = appAuthTransType;
    }

    public byte[] getDefaultDDOL() {
        return defaultDDOL;
    }

    public void setDefaultDDOL(byte[] defaultDDOL) {
        this.defaultDDOL = defaultDDOL;
    }

    public byte[] getRefundTacDenial() {
        return refundTacDenial;
    }

    public void setRefundTacDenial(byte[] refundTacDenial) {
        this.refundTacDenial = refundTacDenial;
    }

    public byte[] getTimeout() {
        return timeout;
    }

    public void setTimeout(byte[] timeout) {
        this.timeout = timeout;
    }

    public byte[] getMemorySlotReadTemp() {
        return memorySlotReadTemp;
    }

    public void setMemorySlotReadTemp(byte[] memorySlotReadTemp) {
        this.memorySlotReadTemp = memorySlotReadTemp;
    }

    public byte[] getMemorySlotUpdateTemp() {
        return memorySlotUpdateTemp;
    }

    public void setMemorySlotUpdateTemp(byte[] memorySlotUpdateTemp) {
        this.memorySlotUpdateTemp = memorySlotUpdateTemp;
    }

    public byte getImpOption() {
        return impOption;
    }

    public void setImpOption(byte impOption) {
        this.impOption = impOption;
    }

    public byte[] getCheckExceptionFilePan() {
        return checkExceptionFilePan;
    }

    public void setCheckExceptionFilePan(byte[] checkExceptionFilePan) {
        this.checkExceptionFilePan = checkExceptionFilePan;
    }
}
