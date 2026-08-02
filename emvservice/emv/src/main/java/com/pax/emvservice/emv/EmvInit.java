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
 * 20210507 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.emv;

import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.init.IModuleInit;
import com.pax.configservice.export.ConfigServiceConstant;
import com.pax.emvlib.utils.EmvUtils;
import com.sankuai.waimai.router.annotation.RouterService;

/**
 * init all required so file by emv module
 */
@RouterService(interfaces = IModuleInit.class,key = ConfigServiceConstant.INIT_EMV)
public class EmvInit implements IModuleInit {
    private IModuleInit.Callback callback;

    @Override
    public void init() {
       BaseApplication.getAppContext().runInBackground(new Runnable() {
           @Override
           public void run() {
               EmvUtils.loadLibrary();
               if (callback != null) {
                   callback.initDone();
                   callback = null;
               }
           }
       });

    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }
}
