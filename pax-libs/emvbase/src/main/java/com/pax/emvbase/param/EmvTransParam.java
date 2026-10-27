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
 * 20210614 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvbase.param;

import androidx.annotation.IntDef;
import com.pax.commonlib.utils.ConvertUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class EmvTransParam {
    //tag 9F02
    private long amount;
    private byte[] amountBytes;
    //tag 9F03,such as cashback
    private long amountOther;
    private byte[] amountOtherBytes;
    //tag 9C
    private byte transType;
    //tag 5F2A, Transaction Currency Code
    private byte[] transCurrencyCode;
    //tag 5F36, Transaction Currency Exponent
    private byte transCurrencyExponent;
    //tag 9A
    private byte[] transDate;
    //9F21
    private byte[] transTime;
    private long transTraceNo;
    private byte[] transTraceNoBytes;

    private byte[] terminalID;
    //1:Use PCI verify offline PIN interface; 0:not use.
    private byte pciMode;
    //When the kernel uses PCI verify offline PIN interface, this parameter is to set the allowed
    // length of PIN. It is a string of the enumeration of 0-12 and separated by ','.
    // For example, '0,4,6' means it is allowed to input 4 or 6 digits for PIN, and to directly
    // press Enter without input PIN.
    private byte[] pinLenSet;
    //Timeout of input PIN, unit: ms, maximum 300,000 ms.
    private int pciTimeout;
    //pan masked pattern
    private String maskPattern;

    private int flowType;

    public static final int FLOWTYPE_COMPLETE = 0x01;
    public static final int FLOWTYPE_SIMPLE = 0x02;
    public static final int FLOWTYPE_QPBOC = 0x03;
    @IntDef({FLOWTYPE_COMPLETE,FLOWTYPE_SIMPLE,FLOWTYPE_QPBOC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FlowType{

    }
    public EmvTransParam() {
        this.transType = 0;
    }

    public EmvTransParam(Builder builder) {
        this.amount = builder.amount;
        this.amountBytes = builder.amountBytes;
        this.amountOther = builder.amountOther;
        this.amountOtherBytes = builder.amountOtherBytes;
        this.transType =  builder.transType;
        this.transCurrencyCode = builder.transCurrencyCode;
        this.transCurrencyExponent = builder.transCurrencyExponent;
        this.transDate = builder.transDate;
        this.transTime = builder.transTime;
        this.transTraceNo = builder.transTraceNo;
        this.transTraceNoBytes = builder.transTraceNoBytes;
        this.terminalID = builder.terminalID;
        this.pciMode = builder.pciMode;
        this.pinLenSet = builder.pinLenSet;
        this.pciTimeout = builder.pciTimeout;
        this.maskPattern = builder.maskPattern;
        this.flowType = builder.flowType;
    }

    public long getAmount() {
        return amount;
    }

    public byte[] getAmountBytes() {
        return amountBytes;
    }

    public long getAmountOther() {
        return amountOther;
    }

    public byte[] getAmountOtherBytes() {
        return amountOtherBytes;
    }

    public byte getTransType() {
        return transType;
    }

    public byte[] getTransDate() {
        return transDate;
    }

    public byte[] getTransTime() {
        return transTime;
    }

    public long getTransTraceNo() {
        return transTraceNo;
    }

    public byte[] getTransTraceNoBytes() {
        return transTraceNoBytes;
    }

    public byte[] getTransCurrencyCode() {
        return transCurrencyCode;
    }

    public byte getTransCurrencyExponent() {
        return transCurrencyExponent;
    }

    /**
     * Get terminal ID bytes.
     *
     * @return Terminal ID bytes
     */
    public byte[] getTerminalID() {
        return terminalID;
    }

    public byte getPciMode() {
        return pciMode;
    }

    public byte[] getPinLenSet() {
        return pinLenSet;
    }

    public int getPciTimeout() {
        return pciTimeout;
    }

    public String getMaskPattern() {
        return maskPattern;
    }

    public int getFlowType() {
        return flowType;
    }

    public static final class Builder {
        //tag 9F02
        private long amount;
        private byte[] amountBytes;
        //tag 9F03,such as cashback
        private long amountOther;
        private byte[] amountOtherBytes;
        //tag 9C
        private byte transType;
        //tag 5F2A, Transaction Currency Code
        private byte[] transCurrencyCode;
        //tag 5F36, Transaction Currency Exponent
        private byte transCurrencyExponent;
        //tag 9A
        private byte[] transDate;
        //9F21
        private byte[] transTime;
        private long transTraceNo;
        private byte[] transTraceNoBytes;

        private byte[] terminalID;

        //1:Use PCI verify offline PIN interface; 0:not use.
        private byte pciMode;
        //When the kernel uses PCI verify offline PIN interface, this parameter is to set the allowed
        // length of PIN. It is a string of the enumeration of 0-12 and separated by ','.
        // For example, '0,4,6' means it is allowed to input 4 or 6 digits for PIN, and to directly
        // press Enter without input PIN.
        private byte[] pinLenSet;
        //Timeout of input PIN, unit: ms, maximum 300,000 ms.
        private int pciTimeout;
        //pan masked pattern
        private String maskPattern;
        private int flowType;
        public Builder() {
            this.amount = 0;
            this.amountOther = 0;
            this.transType = 0;
            this.transCurrencyCode = new byte[0];
            this.transCurrencyExponent = 0;
            this.transDate = new byte[0];
            this.transTime = new byte[0];
            this.transTraceNo = 1;
            this.terminalID = new byte[0];
            this.pciMode = 1;
            this.pinLenSet = new byte[0];
            this.pciTimeout = 60 * 1000;
            this.maskPattern = "";
        }

        public Builder setAmount(long amount) {
            this.amount = amount;
            byte[] amountBytes = new byte[6];
            byte[] tmp = ConvertUtils.strToBcdPaddingLeft(String.valueOf(amount));
            System.arraycopy(tmp, 0, amountBytes, 6 - tmp.length, tmp.length);
            setAmountBytes(amountBytes);
            return this;
        }

        public Builder setAmountBytes(byte[] amount) {
            this.amountBytes = amount;
            return this;
        }

        public Builder setAmountOther(long amountOther) {
            this.amountOther = amountOther;
            byte[] amountOtherBytes = new byte[6];
            byte[] tmp = ConvertUtils.strToBcdPaddingLeft(String.valueOf(amountOther));
            System.arraycopy(tmp, 0, amountOtherBytes, 6 - tmp.length, tmp.length);
            setAmountOtherBytes(amountOtherBytes);
            return this;
        }

        public Builder setAmountOtherBytes(byte[] amountOther) {
            this.amountOtherBytes = amountOther;
            return this;
        }

        public Builder setTransType(byte transType) {
            this.transType = transType;
            return this;
        }

        public Builder setTransCurrencyCode(byte[] transCurrencyCode) {
            this.transCurrencyCode = transCurrencyCode;
            return this;
        }

        public Builder setTransCurrencyExponent(byte transCurrencyExponent) {
            this.transCurrencyExponent = transCurrencyExponent;
            return this;
        }

        public Builder setTransDate(byte[] transDate) {
            this.transDate = transDate;
            return this;
        }

        public Builder setTransTime(byte[] transTime) {
            this.transTime = transTime;
            return this;
        }

        public Builder setTransTraceNo(long transTraceNo) {
            this.transTraceNo = transTraceNo;
            return this;
        }

        public Builder setTransTraceNoBytes(byte[] transTraceNo) {
            this.transTraceNoBytes = transTraceNo;
            return this;
        }

        public Builder setTerminalID(byte[] terminalID) {
            this.terminalID = terminalID;
            return this;
        }

        public Builder setPciMode(byte pciMode) {
            this.pciMode = pciMode;
            return this;
        }

        public Builder setPinLenSet(byte[] pinLenSet) {
            this.pinLenSet = pinLenSet;
            return this;
        }

        public Builder setPciTimeout(int pciTimeout) {
            this.pciTimeout = pciTimeout;
            return this;
        }

        public Builder setMaskPattern(String maskPattern) {
            this.maskPattern = maskPattern;
            return this;
        }


        public Builder setFlowType(@FlowType int flowType) {
            this.flowType = flowType;
            return this;
        }

        public EmvTransParam create(){
            return new EmvTransParam(this);
        }
    }
}
