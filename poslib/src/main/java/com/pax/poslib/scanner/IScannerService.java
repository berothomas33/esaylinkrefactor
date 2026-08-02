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
 * 2021/08/17                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.poslib.scanner;

import android.view.SurfaceHolder;
import androidx.annotation.Nullable;
import com.pax.commonlib.utils.LogUtils;

/**
 * Scanner Service
 */
public interface IScannerService {
    /**
     * Start scan.
     */
    void start();

    /**
     * Cancel scan.
     */
    void cancel();

    /**
     * Set callback.
     *
     * @param callback Scanner callback
     */
    void setCallback(Callback callback);

    /**
     * Set surface holder.
     *
     * @param holder Surface holder. The view shot by the camera will be displayed in this
     * SurfaceHolder.
     */
    void setSurfaceHolder(SurfaceHolder holder);

    interface Callback {
        /**
         * Permissions should be granted before scanning the QR code.
         *
         * @param permissions Permissions List
         * @return Whether the permission is granted
         */
        boolean needPermission(String[] permissions);

        /**
         * Camera is ready, start preview.
         *
         * You should make SurfaceView visible here. If you make the SurfaceView visible too
         * early, then the SurfaceView will not be able to add a Callback, which will result in
         * the inability to preview.
         */
        void startPreview();

        /**
         * Displaying preview.
         *
         * Generally speaking, you should use {@code setSurfaceHolder()} to set the interface for
         * previewing the camera screen instead of using this callback method. This method should
         * only be used for debug.
         *
         * @param data Preview data.
         */
        default void onPreview(@Nullable byte[] data) {
            LogUtils.d("IScannerService", "onPreview");
        }

        /**
         * Decode QR code success.
         *
         * @param result Decode result
         */
        void success(String result);

        /**
         * Device not support scanner, or other error.
         *
         * @param e Exception
         */
        void failed(Exception e);
    }
}
