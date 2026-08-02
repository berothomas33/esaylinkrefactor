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

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.Surface;
import android.view.SurfaceHolder;
import androidx.core.content.ContextCompat;
import com.pax.commonlib.application.ActivityStack;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.entity.DecodeResult;
import com.pax.poslib.model.ModelInfo;
import com.sankuai.waimai.router.annotation.RouterService;

/**
 * Camera Implementation.
 *
 * Use NeptuneLite ScanCodec decode.
 */
@RouterService(interfaces = IScannerService.class, key = ScannerServiceConstants.CAMERA)
public class CameraImpl implements IScannerService, SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final String TAG = "CameraImpl";
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    private IScannerService.Callback callback;
    private SurfaceHolder holder;

    // FIXME 应当更新到 Camera 2.0 或者 CameraX。Camera API 在 Android 5.0 就已经被废弃了
    private Camera camera;
    private boolean isOpen = false;

    @Override
    public void start() {
        BaseApplication.getAppContext().runInBackground(() -> {
            // Check Permission
            if (ContextCompat.checkSelfPermission(BaseApplication.getAppContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(BaseApplication.getAppContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                if (callback != null && !callback.needPermission(new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_PHONE_STATE})) {
                    cancel();
                }
            }
            if (holder != null) {
                LogUtils.d(TAG, "Need preview and add holder");
                holder.addCallback(this);

                // Please be sure to delay for a while before turning on the camera. If the
                // execution is too fast, the camera will report an error "app passed
                // NULL surface".
                BaseApplication.getAppContext().runOnUiThreadDelay(() -> {
                    openCamera();
                    if (callback != null) {
                        callback.startPreview();
                    }
                }, 80);
            }
        });
    }

    private void openCamera() {
        if (!isOpen) {
            try {
                // Get Camera
                camera = Camera.open(ModelInfo.getInstance().getCameraId());
                ScanCodecManager.getInstance().init(BaseApplication.getAppContext(), WIDTH, HEIGHT);

                // Set Camera Param
                Camera.Parameters parameters = camera.getParameters();
                parameters.setPreviewSize(WIDTH, HEIGHT);
                parameters.setPictureSize(WIDTH, HEIGHT);
                parameters.setZoom(parameters.getZoom());
                camera.setParameters(parameters);
                setCameraDisplayOrientation(ActivityStack.getInstance().top(), ModelInfo.getInstance().getCameraId(), camera);
                float bytesPerPixel = ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / (float) 8;
                byte[] data = new byte[(int) (bytesPerPixel * parameters.getPreviewSize().width * parameters.getPreviewSize().height)];
                camera.addCallbackBuffer(data);
                camera.setPreviewCallbackWithBuffer(this);
                LogUtils.i(TAG, "previewFormat:" + parameters.getPreviewFormat() + " bytesPerPixel:" + bytesPerPixel
                        + " prewidth:" + parameters.getPreviewSize().width + " preheight:" + parameters.getPreviewSize().height);
            } catch (Exception e) {
                LogUtils.e(TAG, "openCamera exception", e);
                if (callback != null) {
                    callback.failed(e);
                }
            }
        } else {
            // Already open
            releaseRes();
        }
        isOpen = !isOpen;
    }

    private void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        //See android.hardware.Camera.setCameraDisplayOrientation for documentation.
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int degrees = getDisplayRotation(activity);
        LogUtils.i(TAG, "rotation:-->" + degrees);
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; //compensate the mirror
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    private int getDisplayRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_0:
            default:
                return 0;
        }
    }

    private void releaseRes() {
        try {
            ScanCodecManager.getInstance().release();
            camera.setPreviewCallbackWithBuffer(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        } catch (Exception e) {
            LogUtils.e(e);
        }
    }

    @Override
    public void cancel() {
        LogUtils.d(TAG, "Camera scanner end");
        releaseRes();
        callback = null;
        holder = null;
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void setSurfaceHolder(SurfaceHolder holder) {
        this.holder = holder;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        LogUtils.d(TAG,"onPreviewFrame");
        if (data != null) {
            BaseApplication.getAppContext().runInBackground(() -> {
                if (callback != null) {
                    callback.onPreview(data);
                }
                try {
                    DecodeResult decodeResult = ScanCodecManager.getInstance().decode(data);
                    camera.addCallbackBuffer(data);
                    if (decodeResult.getContent() != null) {
                        if (callback != null) {
                            callback.success(decodeResult.getContent());
                        }
                        if (isOpen) {
                            releaseRes();
                            isOpen = false;
                        }
                    }
                } catch (Exception e) {
                    LogUtils.e(TAG, "", e);
                    if (callback != null) {
                        callback.failed(e);
                    }
                }
            });
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LogUtils.d(TAG, "surfaceCreated");
        if (camera == null) {
            LogUtils.e(TAG, "Camera is null!!!");
            return;
        }
        try {
            camera.setPreviewDisplay(holder);
            LogUtils.d(TAG, "Surface setting ok.");
        } catch (Exception e) {
            LogUtils.e(TAG, "surfaceCreated exception", e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        LogUtils.d(TAG, "surfaceChanged");
        if (holder.getSurface() == null){
            // preview surface does not exist
            LogUtils.d("preview surface does not exist");
            return;
        }
        if (camera==null){
            LogUtils.d("camera = null");
            return;
        }

        // stop preview before making changes
        try {
            camera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            camera.setPreviewDisplay(holder);
            //after stopPreview,you need to setPreviewCallbackWithBuffer to
            // ensure callback to onPreviewFrame
            camera.setPreviewCallbackWithBuffer(this);
            camera.startPreview();

        } catch (Exception e){
            LogUtils.e(TAG, "surfaceChanged exception", e);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtils.d(TAG, "surfaceDestroyed");
        if (camera != null){
            camera.stopPreview();
            LogUtils.d(TAG, "Stop preview.");
        } else {
            LogUtils.d(TAG, "Camera is null.");
        }
    }
}
