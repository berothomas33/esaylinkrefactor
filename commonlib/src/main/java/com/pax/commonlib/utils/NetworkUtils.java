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
 * 20210510 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.commonlib.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import com.pax.commonlib.application.BaseApplication;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.InetAddressValidator;

public class NetworkUtils {
    private NetworkUtils() {
        // do nothing
    }

    /**
     * Check ip format.
     *
     * @param ip the ip
     * @return Whether IP address format validate or not
     */
    public static boolean checkIp(String ip) {
        return InetAddressValidator.getInstance().isValidInet4Address(ip);
    }

    public static boolean checkDomain(String domain) {
        return DomainValidator.getInstance().isValid(domain);
    }

    public static boolean checkHost(String host) {
        return checkIp(host) || checkDomain(host);
    }

    public static boolean checkPort(int port) {
        return port > 0 && port < 65536;
    }

    public static boolean checkUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        return url.matches("^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]$");
    }

    public static boolean checkConnected() {
        ConnectivityManager conn = (ConnectivityManager) BaseApplication.getAppContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (conn != null) {
            NetworkInfo info = conn.getActiveNetworkInfo();
            return info != null && info.isConnected() && info.getState() == NetworkInfo.State.CONNECTED;
        }
        return false;
    }
}
