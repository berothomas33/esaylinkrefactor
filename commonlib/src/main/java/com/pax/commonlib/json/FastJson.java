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
 * 20210526 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.commonlib.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.pax.commonlib.application.BaseApplication;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;

public class FastJson implements IJson{
    /**
     * convert json file in assets to specific type object
     *
     * @param fileName fileName in assets
     * @param type     type
     * @return T object
     */
    @Override
    public <T> T readObjFromAsset(String fileName, Type type) {
        StringBuilder jsonBuilder = new StringBuilder();
        try(
                InputStreamReader reader = new InputStreamReader(BaseApplication.getAppContext().getAssets().open(fileName));
                BufferedReader br = new BufferedReader(reader);
        ){
            String json ="";
            while((json = br.readLine()) != null){
                jsonBuilder.append(json);
            }
        } catch (IOException e) {
            return null;
        }
        return JSON.parseObject(jsonBuilder.toString(),type);
    }

    /**
     * convert json file in assets to a HashMap
     *
     * @param fileName fileName in assets
     * @return HashMap
     */
    @Override
    public HashMap<String, String> readObjFromAsset(String fileName) {
        StringBuilder jsonBuilder = new StringBuilder();
        try(
                InputStreamReader reader = new InputStreamReader(BaseApplication.getAppContext().getAssets().open(fileName));
                BufferedReader br = new BufferedReader(reader);
        ){
            String json ="";
            while((json = br.readLine()) != null){
                jsonBuilder.append(json);
            }
        } catch (IOException e) {
            return null;
        }
        return JSON.parseObject(jsonBuilder.toString(),new TypeReference<HashMap<String,String>>(){});
    }

    /**
     * convert json string to specific type
     *
     * @param json json string
     * @param type type
     * @return T object
     */
    @Override
    public <T> T from(String json, Class<T> type) {
        return JSON.parseObject(json, type);
    }

    /**
     * convert json string to specific type
     *
     * @param json json string
     * @param type type
     * @return T object
     */
    @Override
    public <T> T from(String json, Type type) {
        return JSON.parseObject(json, type);
    }

    /**
     * convert json string to a HashMap
     *
     * @param json json string
     * @return HashMap
     */
    @Override
    public HashMap<String, String> fromMapStr(String json) {
        return JSON.parseObject(json, new TypeReference<HashMap<String, String>>() {});
    }

    /**
     * convert bean to json string
     * @param t bean
     * @param <T> T type bean
     * @return json string
     */
    @Override
    public <T> String to(T t) {
        return JSON.toJSONString(t);
    }
}
