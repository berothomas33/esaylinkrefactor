/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2021-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                           Author                      Action
 * 2022/03/28                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.utils;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.BuildConfig;
import com.pax.jemv.clcommon.ByteArray;

/**
 * EMV Tag Debugger
 *
 * 这个工具类允许传入的都是 Converter, 而不是 String。这是因为数据类型的转换也是需要时间的，所以传入 Converter，
 * 就可以做到仅在需要输出日志的时候才执行转换，从而节约 Release 版本应用的 EMV 流程执行时间。
 *
 * 而之所以要允许设置自定义的 Debugger，主要是为了能够让上层模块“重定向”日志输出的位置。比如 emvdemo 模块中就通过
 * 自定义 Debugger 将日志输出的应用 UI 上。
 */
public class EmvDebugger {
    private static final Debugger DEFAULT_DEBUGGER = (logTag, methodName, tag, value) -> {
        String outputTag = "";
        String outputContent = "";

        if (TextUtils.isEmpty(logTag)) {
            outputTag = "EMV Tag";
        } else {
            outputTag = logTag;
        }

        if (TextUtils.isEmpty(methodName)) {
            outputContent = "tag: " + tag + ", value: " + value;
        } else {
            outputContent = methodName + " - tag: " + tag + ", value: " + value;
        }

        LogUtils.d(outputTag, outputContent);
    };

    private static boolean enableDebug = BuildConfig.DEBUG;
    private static Debugger debugger = DEFAULT_DEBUGGER;

    /**
     * Set Debuggable.
     *
     * If build release package, debug function will be disable by default.
     *
     * @param isDebuggable Debuggable. If this param is {@code false}, then EMV Tag converter and
     * Tag Value converter will not be executed.
     */
    public static void setDebuggable(boolean isDebuggable) {
        enableDebug = isDebuggable;
    }

    /**
     * Set Custom Debugger
     *
     * @param customDebugger Custom Debugger. If this param is null, then will use default
     * debugger. If you want to disable debug function, please use {@code setDebuggable(boolean)}
     * method.
     */
    public static void setCustomDebugger(@Nullable Debugger customDebugger) {
        if (customDebugger != null) {
            debugger = customDebugger;
        } else {
            debugger = DEFAULT_DEBUGGER;
        }
    }

    /**
     * Debug.
     *
     * @param emvTag EMV Tag.
     * @param emvValue EMV Tag Value converter. Only execute when debug function is enabled.
     */
    public static void d(int emvTag, Converter<String> emvValue) {
        d(null, emvTag, emvValue);
    }

    /**
     * Debug.
     *
     * @param emvTag EMV Tag converter. Only execute when debug function is enabled.
     * @param emvValue EMV Tag Value converter. Only execute when debug function is enabled.
     */
    public static void d(@NonNull Converter<String> emvTag, @NonNull Converter<String> emvValue) {
        d(null, emvTag, emvValue);
    }

    /**
     * Debug.
     *
     * @param logTag Log Tag.
     * @param emvTag EMV Tag.
     * @param emvValue EMV Tag Value converter. Only execute when debug function is enabled.
     */
    public static void d(@Nullable String logTag, int emvTag, @NonNull Converter<String> emvValue) {
        d(logTag, null, emvTag, emvValue);
    }

    /**
     * Debug.
     *
     * @param logTag Log Tag.
     * @param emvTag EMV Tag converter. Only execute when debug function is enabled.
     * @param emvValue EMV Tag Value converter. Only execute when debug function is enabled.
     */
    public static void d(@Nullable String logTag, @NonNull Converter<String> emvTag,
            @NonNull Converter<String> emvValue) {
        d(logTag, null, emvTag, emvValue);
    }

    public static void d(@Nullable String logTag,
            @Nullable String methodName,
            int emvTag,
            @NonNull ByteArray emvValue) {
        d(logTag, methodName,
                emvTag,
                () -> ConvertUtils.bcd2Str(emvValue.data, emvValue.length));
    }

    public static void d(@Nullable String logTag,
            @Nullable String methodName,
            int emvTag,
            @Nullable byte[] emvValue) {
        d(logTag, methodName, emvTag,
                () -> ConvertUtils.bcd2Str(emvValue));
    }

    /**
     * Debug.
     *
     * @param logTag Log Tag.
     * @param methodName Method Name.
     * @param emvTag EMV Tag.
     * @param emvValue EMV Tag Value converter. Only execute when debug function is enabled.
     */
    public static void d(@Nullable String logTag,
            @Nullable String methodName,
            int emvTag,
            @NonNull Converter<String> emvValue) {
        d(logTag, methodName, () -> ConvertUtils.bcd2Str(ConvertUtils.intToByteArray(emvTag,
                ConvertUtils.EEndian.BIG_ENDIAN)), emvValue);
    }

    public static void d(@Nullable String logTag,
            @Nullable String methodName,
            @NonNull byte[] emvTag,
            @NonNull ByteArray emvValue) {
        d(logTag, methodName, () -> ConvertUtils.bcd2Str(emvTag),
                () -> ConvertUtils.bcd2Str(emvValue.data, emvValue.length));
    }

    public static void d(@Nullable String logTag,
            @Nullable String methodName,
            @NonNull byte[] emvTag,
            @Nullable byte[] emvValue) {
        d(logTag, methodName, () -> ConvertUtils.bcd2Str(emvTag),
                () -> ConvertUtils.bcd2Str(emvValue));
    }

    public static void d(@Nullable String logTag,
            @Nullable String methodName,
            @NonNull byte[] emvTag,
            @NonNull Converter<String> emvValue) {
        d(logTag, methodName, () -> ConvertUtils.bcd2Str(emvTag), emvValue);
    }

    /**
     * Debug.
     *
     * @param logTag Log Tag.
     * @param methodName Method Name.
     * @param emvTag EMV Tag converter. Only execute when debug function is enabled.
     * @param emvValue EMV Tag Value converter. Only execute when debug function is enabled.
     */
    public static void d(@Nullable String logTag,
            @Nullable String methodName,
            @NonNull Converter<String> emvTag,
            @NonNull Converter<String> emvValue) {
        if (enableDebug && debugger != null) {
            String tag = emvTag.onConvert();
            String value = emvValue.onConvert();
            if (tag != null && !tag.isEmpty()) {
                debugger.d(logTag, methodName, tag, value);
            }
        }
    }

    public interface Debugger {
        /**
         * Debug.
         *
         * @param logTag Log Tag.
         * @param methodName Method Name.
         * @param emvTag EMV Tag.
         * @param tagValue EMV Tag Value.
         */
        void d(@Nullable String logTag,
                @Nullable String methodName,
                @NonNull String emvTag,
                @Nullable String tagValue);
    }

    public interface Converter<O> {
        /**
         * Convert.
         *
         * @return Convert result.
         */
        @Nullable
        O onConvert();
    }
}
