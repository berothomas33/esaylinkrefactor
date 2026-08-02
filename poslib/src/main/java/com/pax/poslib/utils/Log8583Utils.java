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
package com.pax.poslib.utils;

import android.os.Environment;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.gl.pack.IIso8583;
import com.pax.gl.pack.exception.Iso8583Exception;
import com.pax.poslib.gl.impl.GL;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

/**
 * The type Log 8583 utils.
 */
public class Log8583Utils {

    private static final String TAG = "Log8583Utils";

    private Log8583Utils() {
        //do nothing
    }

    /**
     * Save log.
     *
     * @param fileName the file name
     * @param data     the data
     * @param isSend   the is send
     */
    public static void saveLog(String fileName, byte[] data, boolean isSend) {
        IIso8583 iso8583 = GL.getGL().getPacker().getIso8583();
        HashMap<String, byte[]> map;
        try {
            map = iso8583.unpack(data, true);
        } catch (Iso8583Exception e) {
            LogUtils.e(TAG, "", e);
            return;
        }

        File file = openFile(fileName);
        if (file == null) {
            return;
        }

        String title;
        if (isSend) {
            title = "Send Data";
        } else {
            title = "Recv Data";
        }

        writeToFile(title, map, file);
    }

    private static File openFile(String fileName) {
        String path = Environment.getExternalStorageDirectory().getPath() + "/logs/8583log/";
        File dir = new File(path);
        if (dir.exists() || dir.mkdirs()) {
            return new File(path, fileName);
        }

        return null;
    }

    private static void writeToFile(String title, HashMap<String, byte[]> map, File file) {
        try (BufferedWriter output = new BufferedWriter(new FileWriter(file, true))) {
            output.write(title + "\n\n");

            writeLine("H", map.get("h"), output);
            writeLine("M", map.get("m"), output);

            for (int i = 0; i <= 64; ++i) {
                String tag = Integer.toString(i);
                writeLine(tag, map.get(tag), output);
            }
            writeLine("123", map.get("123"), output);

            output.write("\n\n");

            output.flush();
        } catch (IOException e) {
            LogUtils.e(TAG, "", e);
        }
    }

    private static void writeLine(String tag, byte[] value, BufferedWriter output) throws IOException {
        if (value == null || value.length <= 0) {
            return;
        }

        String writeValue;
        String writeTag = tag;
        if ("52".equals(writeTag) || "55".equals(writeTag) || "56".equals(writeTag) || "62".equals(writeTag) || "63".equals(writeTag) || "123".equals(writeTag)) {
            writeValue = ConvertUtils.bcd2Str(value);
        } else {
            writeValue = new String(value);
        }

        output.write("[" + writeTag + "] : " + writeValue + "\n");
    }
}
