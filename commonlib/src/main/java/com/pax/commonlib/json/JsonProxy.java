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

import java.lang.reflect.Type;
import java.util.HashMap;

public class JsonProxy implements IJson{
    private IJson iJson;
    private static final class LazyHolder{
        private static final JsonProxy INSTANCE = new JsonProxy();
    }
    public static JsonProxy getInstance(){
        return LazyHolder.INSTANCE;
    }

    public void init(IJson iJson){
        this.iJson = iJson;
    }
    /**
     * convert json file in assets to specific type object
     *
     * @param fileName fileName in assets
     * @param type     type
     * @return T object
     */
    @Override
    public <T> T readObjFromAsset(String fileName, Type type) {
        return iJson.readObjFromAsset(fileName,type);
    }

    /**
     * convert json file in assets to a HashMap
     *
     * @param fileName fileName in assets
     * @return HashMap
     */
    @Override
    public HashMap<String, String> readObjFromAsset(String fileName) {
        return iJson.readObjFromAsset(fileName);
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
        return iJson.from(json,type);
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
        return iJson.from(json, type);
    }

    /**
     * convert json string to a HashMap
     *
     * @param json json string
     * @return HashMap
     */
    @Override
    public HashMap<String, String> fromMapStr(String json) {
        return iJson.fromMapStr(json);
    }

    /**
     * convert bean to json string
     * @param t bean
     * @param <T> T type bean
     * @return json string
     */
    @Override
    public <T> String to(T t) {
        return iJson.to(t);
    }
}
