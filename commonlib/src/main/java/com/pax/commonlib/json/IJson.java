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

public interface IJson {
    /**
     * convert json file in assets to specific type object
     * @param fileName fileName in assets
     * @param type type
     * @param <T> type
     * @return T object
     */
    <T> T readObjFromAsset(String fileName, Type type);

    /**
     * convert json file in assets to a HashMap
     * @param fileName fileName in assets
     * @return HashMap
     */
    HashMap<String,String> readObjFromAsset(String fileName);

    /**
     * convert json string to specific type
     * @param json json string
     * @param type type
     * @param <T> type
     * @return T object
     */
    <T> T from(String json, Class<T> type);

    /**
     * convert json string to specific type
     *
     * @param json json string
     * @param type type
     * @return T object
     */
    <T> T from(String json, Type type);

    /**
     *  convert json string to a HashMap
     * @param json json string
     * @return HashMap
     */
    HashMap<String, String> fromMapStr(String json);

    /**
     * convert bean to json string
     * @param t bean
     * @param <T> T type bean
     * @return json string
     */
    <T> String to(T t);
}
