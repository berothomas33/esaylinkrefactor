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
 * 2021/12/17                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.commonlib.result;

import android.content.Intent;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

/**
 * Used to start the Activity and get the returned result.<br>
 * Please note that you must create an instance of this class before the execution reaches the
 * onStart life cycle. Otherwise, an exception will be thrown.
 * <br><br>
 * 用来启动 Activity 并获取返回结果<br>
 * 请注意，一定要在执行到 onStart 生命周期之前创建该类的实例。否则将会抛出异常
 */
public class StartActivityLauncher extends BaseActivityResultLauncher<Intent, ActivityResult> {

    /**
     * Create a launcher to start the Activity and get the returned result.
     * <br>
     * Please note that you must create an instance of this class before the execution reaches the
     * onStart life cycle. Otherwise, an exception will be thrown.
     * <br><br>
     * 创建一个用来启动 Activity 并获取返回结果的 launcher
     * <br>
     * 请注意，一定要在执行到onStart生命周期之前创建该类的实例。否则将会抛出异常
     *
     * @param caller Subclass of ComponentActivity or Fragment.<br>
     *               ComponentActivity 或者 Fragment 的子类。
     */
    public StartActivityLauncher(ActivityResultCaller caller) {
        super(caller, new ActivityResultContracts.StartActivityForResult());
    }

    /**
     *
     * @param intent
     * @param callback
     */
    public void launch(Intent intent, OnResultCallback callback) {
        launchInternal(intent, result -> callback.onResult(result.getResultCode(), result.getData()));
    }

    public interface OnResultCallback {
        void onResult(int resultCode, @Nullable Intent data);
    }
}
