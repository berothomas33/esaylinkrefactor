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

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import com.pax.commonlib.utils.LogUtils;

/**
 * File description
 *
 * @author yehongbo
 * @date 2021/12/17
 */
public abstract class BaseActivityResultLauncher<I, O> {
    private final ActivityResultLauncher<I> launcher;
    private ActivityResultCallback<O> callback;

    public BaseActivityResultLauncher(ActivityResultCaller caller,
            ActivityResultContract<I, O> contract) {
        launcher = caller.registerForActivityResult(contract, result -> {
            if (callback != null) {
                callback.onActivityResult(result);
                callback = null;
            }
        });
    }

    protected void launchInternal(I input, ActivityResultCallback<O> callback) {
        LogUtils.d(this.getClass().getSimpleName(), "launch");
        this.callback = callback;
        launcher.launch(input);
    }
}
