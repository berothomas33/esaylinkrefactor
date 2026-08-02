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
 * 20210430 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.configservice.impl;

import com.pax.commonlib.utils.ConvertUtils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
        byte[] bytes = ConvertUtils.strToBcdPaddingLeft("FF");
        System.out.println(bytes);
        byte[] bytes1 = ConvertUtils.strToBcdPaddingRight("FF");
        System.out.println(bytes1);

        byte[] bytes3 = ConvertUtils.strToBcdPaddingLeft("00000011");
        System.out.println(bytes1);

        byte[] bytes2 = "xxxxxxxx".getBytes();
        byte[] bytes4 = ConvertUtils.strToBcdPaddingLeft("xybdfdfa");
        System.out.println(bytes4);

//        byte[] bytes4df = ConvertUtils.strToBcdPaddingLeft("1000");
//        byte[] bytes4dfd = ConvertUtils.strToBcdPaddingRight("1000");
        byte[] bytes4df = ConvertUtils.strToBcdPaddingLeft("F8");
        byte[] bytes4dfd = ConvertUtils.strToBcdPaddingRight("F8");
//        byte[] xx = new byte[]{0x10, 0x00};
        byte[] xx = new byte[]{(byte) 0xF8};
        System.out.println(bytes4);
    }
}