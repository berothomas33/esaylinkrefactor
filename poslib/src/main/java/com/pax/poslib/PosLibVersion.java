/*
 *  * ===========================================================================================
 *  * = COPYRIGHT
 *  *          PAX Computer Technology(Shenzhen); CO., LTD PROPRIETARY INFORMATION
 *  *   This software is supplied under the terms of a license agreement or nondisclosure
 *  *   agreement with PAX Computer Technology(Shenzhen); CO., LTD and may not be copied or
 *  *   disclosed except in accordance with the terms in that agreement.
 *  *     Copyright (C); 2019-? PAX Computer Technology(Shenzhen); CO., LTD All rights reserved.
 *  * Description: // Detail description about the voidction of this module,
 *  *             // interfaces with the other modules, and dependencies.
 *  * Revision History:
 *  * Date                  Author	                 Action
 *  * 20200824  	         xieYb                   Modify
 *  * ===========================================================================================
 *
 */
package com.pax.poslib;

import com.pax.commonlib.application.BaseApplication;
import com.pax.poslib.neptune.Sdk;

public class PosLibVersion {
    private PosLibVersion() {
        // do nothing
    }

    public static String getGlCommVersion(){
        return "v1.09.00_20211230";
    }
    public static String getGlExPrinterVersion(){
        return "v1.01.01_20191225";
    }
    public static String getGlPackerVersion(){
        return "v1.05.00_20211230";
    }
    public static String getGlImprocessingVersion(){
        return "v1.03.00_T_20220121";
    }
    public static String getGlUtilsVersion(){
        return "v1.01.00_T_20220121";
    }
    public static String getGlBaiFuTongVersion(){
        return "v1.00.00_20180119";
    }
    public static String getBaseLinkApiVersion(){
        return "v1.03.00_T_20190122";
    }
    public static String getNeptuneLiteApiVersion(){
        return Sdk.getInstance().getDal(BaseApplication.getAppContext()).getSys().getDevInterfaceVer();
    }
}
