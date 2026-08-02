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
 * 20210429 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.configservice.export;


import java.util.HashMap;
import java.util.Map;

public interface IConfigParamService {
    /**
     * update config param form PAXSTORE
     * @param map all config params in a map
     */
    void updateKeyValueParam(HashMap<String,String> map);

    /**
     * get string config param
     * @param key string key
     * @return string value
     */
    public String getString(String key);

    /**
     * get string config param with defaultValue
     * @param key string key
     * @param defaultValue defaultValue
     * @return string value
     */
    public String getString(String key,String defaultValue);

    /**
     * save string config param with defaultValue
     * @param key string key
     * @param value string value
     */
    public void putString(String key, String value);

    /**
     * save int config param with defaultValue
     * @param key string key
     * @param value int value
     */
    public void putInt(String key, int value);

    /**
     * get int config param
     * @param key string key
     * @return int value
     */
    public int getInt(String key);

    /**
     *  int config param with defaultValue
     * @param key string key
     * @param defaultValue defaultValue
     * @return int value
     */
    public int getInt(String key,int defaultValue);

    /**
     * save boolean config param with defaultValue
     * @param key string key
     * @param value boolean value
     */
    public void putBoolean(String key, boolean value);

    /**
     * get boolean config param
     * @param key string key
     * @return boolean value
     */
    public boolean getBoolean(String key);

    /**
     * get boolean config param with defaultValue
     * @param key string key
     * @param defaultValue defaultValue
     * @return boolean value
     */
    public boolean getBoolean(String key,boolean defaultValue);

    /**
     * get all config param with a map
     * @return map
     */
    public Map<String, String> getAll();
}
