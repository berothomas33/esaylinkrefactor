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

import android.text.TextUtils;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.sp.SharedPrefUtil;
import com.pax.configservice.export.ConfigServiceConstant;
import com.pax.configservice.export.IConfigParamService;
import com.sankuai.waimai.router.annotation.RouterService;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RouterService(interfaces = IConfigParamService.class,key = ConfigServiceConstant.CONFIGSERVICE_CONFIG,singleton = true)
public class ConfigParamService implements IConfigParamService {
    SharedPrefUtil defaultSp = new SharedPrefUtil(BaseApplication.getAppContext());
    /**
     * update config param form PAXSTORE
     * @param map all config params in a map
     */
    @Override
    public void updateKeyValueParam(HashMap<String, String> map) {
        Set<Map.Entry<String, String>> entries = map.entrySet();
        for (Map.Entry<String, String> entry : entries){
            defaultSp.putString(entry.getKey(),entry.getValue());
        }
    }

    /**
     * get string config param
     * @param key string key
     * @return string value
     */
    @Override
    public String getString(String key) {
        return defaultSp.getString(key);
    }

    /**
     * get string config param with defaultValue
     * @param key string key
     * @param defaultValue defaultValue
     * @return string value
     */
    @Override
    public String getString(String key, String defaultValue) {
        return defaultSp.getString(key,defaultValue);
    }

    /**
     * save string config param with defaultValue
     * @param key string key
     * @param value string value
     */
    @Override
    public void putString(String key, String value) {
        defaultSp.putString(key,value);
    }

    /**
     * save int config param with defaultValue
     * @param key string key
     * @param value int value
     */
    @Override
    public void putInt(String key, int value) {
        defaultSp.putString(key, String.valueOf(value));
    }

    /**
     * get int config param
     * @param key string key
     * @return int value
     */
    @Override
    public int getInt(String key) {
        return getInt(key,-1);
    }

    /**
     *  int config param with defaultValue
     * @param key string key
     * @param defaultValue defaultValue
     * @return int value
     */
    @Override
    public int getInt(String key, int defaultValue) {
        String value = defaultSp.getString(key);
        if (TextUtils.isEmpty(value)){
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    /**
     * save boolean config param with defaultValue
     * @param key string key
     * @param value boolean value
     */
    @Override
    public void putBoolean(String key, boolean value) {
        defaultSp.putString(key, String.valueOf(value));
    }

    /**
     * get boolean config param
     * @param key string key
     * @return boolean value
     */
    @Override
    public boolean getBoolean(String key) {
        return getBoolean(key,false);
    }

    /**
     * get boolean config param with defaultValue
     * @param key string key
     * @param defaultValue defaultValue
     * @return boolean value
     */
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = defaultSp.getString(key);
        if (TextUtils.isEmpty(value)){
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * get all config param with a map
     * @return map
     */
    @Override
    public Map<String, String> getAll() {
        return (Map<String, String>) defaultSp.getAll();
    }
}
