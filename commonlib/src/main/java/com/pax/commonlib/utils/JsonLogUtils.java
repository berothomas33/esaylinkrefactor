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
 * 20210519 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.commonlib.utils;

import android.os.Environment;
import com.pax.commonlib.json.JsonProxy;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * utils for save online log to file
 */
public class JsonLogUtils {
    private static final String TAG = "JsonLogUtils";
    private JsonLogUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    /**
     * save online log to file
     * @param fileName fileName
     * @param data online log content
     */
    public static void saveLog(String fileName, String data) {
        File file = openFile(fileName);
        if (file == null) {
            return;
        }
        HashMap<String, String> map = JsonProxy.getInstance().fromMapStr(data);
        writeToFile(map, file);
    }

    /**
     * save map content to file
     * @param map map content
     * @param file file to save
     */
    private static void writeToFile(HashMap<String, String> map, File file) {
        try (BufferedWriter output = new BufferedWriter(new FileWriter(file, true))) {
            Set<Map.Entry<String, String>> entries = map.entrySet();
            for (Map.Entry<String, String> entry : entries){
                writeLine(entry.getKey(), entry.getValue(), output);
            }
            output.write("\n\n");

            output.flush();
        } catch (IOException e) {
            LogUtils.e(TAG, "", e);
        }
    }

    /**
     * write key value through BufferedWriter
     * @param tag key
     * @param value value
     * @param output BufferedWriter
     * @throws IOException IOException
     */
    private static void writeLine(String tag, String value, BufferedWriter output) throws IOException {
        output.write("[" + tag + "] : " + value + "\n");
    }

    private static File openFile(String fileName) {
        String path = Environment.getExternalStorageDirectory().getPath() + "/logs/jsonLog/";
        File dir = new File(path);
        if (dir.exists() || dir.mkdirs()) {
            return new File(path, fileName);
        }
        return null;
    }
}
