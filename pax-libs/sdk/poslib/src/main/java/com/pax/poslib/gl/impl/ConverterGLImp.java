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
 * Date                  Author	                 Action
 * 20190108  	         Kim.L                   Create
 * ===========================================================================================
 */
package com.pax.poslib.gl.impl;

import com.pax.commonlib.utils.LogUtils;
import com.pax.gl.utils.impl.Convert;
import com.pax.poslib.gl.convert.IConvert;

public class ConverterGLImp implements IConvert {

    public ConverterGLImp() {
        // do nothing
    }

    /**
     * convert bcd to string
     * @param b bcd bytes
     * @return string
     */
    public String bcdToStr(byte[] b) {
        try {
            return Convert.bcdToStr(b);
        } catch (IllegalArgumentException e) {
            LogUtils.e(e);
            return "";
        }
    }

    @Override
    public byte[] strToBcdPaddingLeft(String str) {
        return strToBcd(str, EPaddingPosition.PADDING_LEFT);
    }

    @Override
    public byte[] strToBcdPaddingRight(String str) {
        return strToBcd(str, EPaddingPosition.PADDING_RIGHT);
    }

    /**
     * convert string to bcd bytes
     * @param str string
     * @param paddingPosition padding position
     * @return bcd bytes
     */
    public byte[] strToBcd(String str, EPaddingPosition paddingPosition) {
        byte[] result = new byte[0];
        try {
            if (paddingPosition == EPaddingPosition.PADDING_RIGHT)
                result = Convert.strToBcd(str, Convert.EPaddingPosition.PADDING_RIGHT);
            else {
                result = Convert.strToBcd(str, Convert.EPaddingPosition.PADDING_LEFT);
            }
        }catch (IllegalArgumentException e){
            LogUtils.e(e);
        }
        return result;
    }

    /**
     * convert int to byte array
     * @param paramInt1 int value
     * @param paramArrayOfByte byte array
     * @param paramInt2 begin position
     * @param paramEEndian endian
     */
    public void intToByteArray(int paramInt1, byte[] paramArrayOfByte, int paramInt2, EEndian paramEEndian) {
        try {
            if (paramEEndian == EEndian.BIG_ENDIAN)
                Convert.intToByteArray(paramInt1, paramArrayOfByte, paramInt2, Convert.EEndian.BIG_ENDIAN);
            else {
                Convert.intToByteArray(paramInt1, paramArrayOfByte, paramInt2, Convert.EEndian.LITTLE_ENDIAN);
            }
        }catch (IllegalArgumentException e){
            LogUtils.e(e);
        }
    }

    /**
     * convert int to byte array
     * @param paramInt int value
     * @param paramEEndian endian
     * @return byte array
     */
    public byte[] intToByteArray(int paramInt, EEndian paramEEndian) {
        if (paramEEndian == EEndian.BIG_ENDIAN)
            return Convert.intToByteArray(paramInt, Convert.EEndian.BIG_ENDIAN);
        else {
            return Convert.intToByteArray(paramInt, Convert.EEndian.LITTLE_ENDIAN);
        }
    }

}
