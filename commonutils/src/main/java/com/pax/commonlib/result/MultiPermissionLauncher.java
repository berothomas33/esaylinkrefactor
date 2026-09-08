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
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Used to start the authorization dialog.<br>
 * Please note that you must create an instance of this class before the execution reaches the
 * onStart life cycle. Otherwise, an exception will be thrown.
 * <br><br>
 * 用来启动授权对话框<br>
 * 请注意，一定要在执行到 onStart 生命周期之前创建该类的实例。否则将会抛出异常
 */
public class MultiPermissionLauncher extends BaseActivityResultLauncher<String[], Map<String, Boolean>> {

    /**
     * Create a launcher to start the authorization dialog.
     * <br>
     * Please note that you must create an instance of this class before the execution reaches the
     * onStart life cycle. Otherwise, an exception will be thrown.
     * <br><br>
     * 创建一个用来启动授权对话框的 launcher
     * <br>
     * 请注意，一定要在执行到onStart生命周期之前创建该类的实例。否则将会抛出异常
     *
     * @param caller Subclass of ComponentActivity or Fragment.<br>
     *               ComponentActivity 或者 Fragment 的子类。
     */
    public MultiPermissionLauncher(ActivityResultCaller caller) {
        super(caller, new ActivityResultContracts.RequestMultiplePermissions());
    }

    /**
     * Request permission.
     * <br><br>
     * 请求授予权限
     *
     * @param permissions   All permissions that need to be granted.<br>
     *                      所有需要授予的权限
     * @param callback      Request permission result callback.<br>
     *                      请求权限结果回调
     */
    public void launch(@NonNull String[] permissions, OnGrantResultCallback callback) {
        List<String> denyList = new LinkedList<>();
        launchInternal(permissions, (ActivityResultCallback<Map<String, Boolean>>) result -> {
            for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                if (entry.getValue() == null || !entry.getValue()) {
                    denyList.add(entry.getKey());
                }
            }
            if (callback != null) {
                callback.onGrantResult(denyList);
            }
        });
    }

    /**
     * Request permission result callback.
     */
    public interface OnGrantResultCallback {
        /**
         * Grant result.
         *
         * @param denyList Deny permissions list. If all permissions granted, the list is empty.
         */
        void onGrantResult(@NonNull List<String> denyList);
    }
}
