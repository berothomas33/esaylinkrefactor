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
package com.pax.poslib.gl.convert;

public interface IConvert {
    //convert bcd to string
    String bcdToStr(byte[] b);

    byte[] strToBcdPaddingLeft(String str);

    byte[] strToBcdPaddingRight(String str);
    //convert string to bcd bytes
    byte[] strToBcd(String str, EPaddingPosition paddingPosition);
    //convert int to byte array
    void intToByteArray(int paramInt1, byte[] paramArrayOfByte, int paramInt2, EEndian paramEEndian);
    //convert int to byte array
    byte[] intToByteArray(int paramInt, EEndian paramEEndian);

    enum EEndian {
        LITTLE_ENDIAN,
        BIG_ENDIAN,
    }

    enum EPaddingPosition {
        PADDING_LEFT,
        PADDING_RIGHT,
    }
}