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
 * 2021/12/21                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.commonlib.utils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lazy Init Helper
 */
public class LazyInit<T> {
    private static final String TAG = "LazyInit";

    private final Callback<T> callback;
    private final AtomicBoolean isInit = new AtomicBoolean(false);
    private T result;

    private LazyInit(Callback<T> callback) {
        this.callback = callback;
    }

    /**
     * Create lazy init helper
     *
     * @param callback Init callback
     * @param <T> Return type
     * @return Lazy init helper instance
     */
    public static <T> LazyInit<T> by(Callback<T> callback) {
        return new LazyInit<>(callback);
    }

    /**
     * Init now
     */
    public void init() {
        if (isInit.get()) {
            return;
        }
        performInit();
    }

    /**
     * Get init value
     *
     * @return Init value
     */
    public T get() {
        if (isInit.get()) {
            return result;
        }
        performInit();
        return result;
    }

    private synchronized void performInit() {
        if (!isInit.get()) {
            LogUtils.d(TAG, "Start init");
            result = callback.init();
            isInit.set(true);
        } else {
            LogUtils.d(TAG, "Already init");
        }
    }

    public boolean isInit() {
        return isInit.get();
    }

    public interface Callback<T> {
        T init();
    }
}
