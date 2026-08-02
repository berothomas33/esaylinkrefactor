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
package com.pax.commonlib.abl.utils;

import com.pax.commonlib.utils.LogUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * encryption utils
 */
public class EncUtils {
    private static final String TAG = "EncUtils";

    private EncUtils() {
        //do nothing
    }

    /**
     * Password SHA calc
     *
     * @param str input data
     * @return sha1 value
     */
    public static String pwdSha(String str) {
        return sha256(str);
    }

    /**
     * SHA-256 calc
     *
     * @param str input data
     * @return SHA-256 value
     */
    public static String sha256(String str) {
        return sha(str, "SHA-256");
    }

    /**
     * SHA-512 calc
     *
     * @param str input data
     * @return SHA-512 value
     */
    public static String sha512(String str) {
        return sha(str, "SHA-512");
    }

    private static String sha(String str, String type) {
        try {
            MessageDigest digest = MessageDigest.getInstance(type);
            digest.update(str.getBytes());
            byte[] messageDigest = digest.digest();
            // Create Hex String
            StringBuilder hexString = new StringBuilder();

            for (byte i : messageDigest) {
                String shaHex = Integer.toHexString(i & 0xFF);
                if (shaHex.length() < 2) {
                    hexString.append(0);
                }
                hexString.append(shaHex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            LogUtils.w(TAG, e);
        }
        return "";
    }
}
