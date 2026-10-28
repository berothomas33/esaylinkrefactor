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
import com.pax.poslib.gl.convert.IConvert;

public class ConverterImp implements IConvert {
    private static final char[] ARRAY_OF_CHAR = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public ConverterImp() {
        // do nothing
    }

    /**
     * convert bcd to string
     * @param b bcd bytes
     * @return string
     */
    public String bcdToStr(byte[] b) {
        String result = "";
        if (b == null) {
            LogUtils.e(new IllegalArgumentException("bcdToStr input arg is null"));
            return result;
        }

        StringBuilder localStringBuilder = new StringBuilder(b.length * 2);
        for (byte i : b) {
            localStringBuilder.append(ARRAY_OF_CHAR[((i & 0xF0) >>> 4)]);
            localStringBuilder.append(ARRAY_OF_CHAR[(i & 0xF)]);
        }

        return localStringBuilder.toString();
    }

    private static int strByte2Int(byte b) {
        int j;
        if ((b >= 'a') && (b <= 'z')) {
            j = b - 'a' + 0x0A;
        } else {
            if ((b >= 'A') && (b <= 'Z'))
                j = b - 'A' + 0x0A;
            else
                j = b - '0';
        }
        return j;
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
    public byte[] strToBcd(String str, IConvert.EPaddingPosition paddingPosition) {
        if ((str == null) || (paddingPosition == null)) {
            LogUtils.e(new IllegalArgumentException("bcdToStr input arg is null"));
            return new byte[0];
        }
        String s = str;
        int len = s.length();
        if (len % 2 != 0) {
            if (paddingPosition == IConvert.EPaddingPosition.PADDING_RIGHT)
                s = s + "0";
            else {
                s = "0" + s;
            }
            len = s.length();
        }
        if (len >= 2) {
            len /= 2;
        }
        byte[] bcd = new byte[len];
        byte[] strBytes = s.getBytes();

        for (int p = 0; p < strBytes.length / 2; p++) {
            bcd[p] = (byte) ((strByte2Int(strBytes[(2 * p)]) << 4) + strByte2Int(strBytes[(2 * p + 1)]));
        }

        return bcd;
    }
    /**
     * convert int to byte array
     * @param paramInt1 int value
     * @param paramArrayOfByte byte array
     * @param paramInt2 begin position
     * @param paramEEndian endian
     */
    public void intToByteArray(int paramInt1, byte[] paramArrayOfByte, int paramInt2, IConvert.EEndian paramEEndian) {
        if ((paramArrayOfByte == null) || (paramEEndian == null)) {
            LogUtils.e(new IllegalArgumentException("longToByteArray input arg is null"));
            return;
        }

        if (paramEEndian == IConvert.EEndian.BIG_ENDIAN) {
            paramArrayOfByte[paramInt2] = (byte) (paramInt1 >>> 24 & 0xFF);
            paramArrayOfByte[(paramInt2 + 1)] = (byte) (paramInt1 >>> 16 & 0xFF);
            paramArrayOfByte[(paramInt2 + 2)] = (byte) (paramInt1 >>> 8 & 0xFF);
            paramArrayOfByte[(paramInt2 + 3)] = (byte) (paramInt1 & 0xFF);
            return;
        }
        paramArrayOfByte[paramInt2] = (byte) (paramInt1 & 0xFF);
        paramArrayOfByte[(paramInt2 + 1)] = (byte) (paramInt1 >>> 8 & 0xFF);
        paramArrayOfByte[(paramInt2 + 2)] = (byte) (paramInt1 >>> 16 & 0xFF);
        paramArrayOfByte[(paramInt2 + 3)] = (byte) (paramInt1 >>> 24 & 0xFF);
    }
    /**
     * convert int to byte array
     * @param paramInt int value
     * @param paramEEndian endian
     * @return byte array
     */
    public byte[] intToByteArray(int paramInt, IConvert.EEndian paramEEndian) {
        if (paramEEndian == null) {
            LogUtils.e(new IllegalArgumentException("intToByteArray input arg is null"));
            return new byte[0];
        }

        byte[] arrayOfByte = new byte[4];

        if (paramEEndian == IConvert.EEndian.BIG_ENDIAN) {
            arrayOfByte[0] = (byte) (paramInt >>> 24 & 0xFF);
            arrayOfByte[1] = (byte) (paramInt >>> 16 & 0xFF);
            arrayOfByte[2] = (byte) (paramInt >>> 8 & 0xFF);
            arrayOfByte[3] = (byte) (paramInt & 0xFF);
        } else {
            arrayOfByte[0] = (byte) (paramInt & 0xFF);
            arrayOfByte[1] = (byte) (paramInt >>> 8 & 0xFF);
            arrayOfByte[2] = (byte) (paramInt >>> 16 & 0xFF);
            arrayOfByte[3] = (byte) (paramInt >>> 24 & 0xFF);
        }

        return arrayOfByte;
    }
}
