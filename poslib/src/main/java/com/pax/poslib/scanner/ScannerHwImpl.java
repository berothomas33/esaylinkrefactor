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
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IScannerHw;
import com.pax.dal.entity.ScanResult;
import com.pax.dal.exceptions.EScannerHwDevException;
import com.pax.dal.exceptions.ScannerHwDevException;
import com.pax.poslib.neptune.Sdk;
import com.sankuai.waimai.router.annotation.RouterService;

/**
 * NeptuneLite ScannerHw Implementation.
 *
 * Some devices support hardware scan code function, such as A920Pro, A920Mini. However, unless
 * the camera is not available, CameraImpl should be used. Because ScannerHwImpl does not support
 * the preview function.
 */
@RouterService(interfaces = IScannerService.class, key = ScannerServiceConstants.SCANNERHW)
public class ScannerHwImpl implements IScannerService {
    private static final String TAG = "ScannerHwImpl";

    private IScannerService.Callback callback;
    private IScannerHw scannerHw;

    private volatile boolean continueScan = true;

    @Override
    public void start() {
        BaseApplication.getAppContext().runInBackground(() -> {
            scannerHw = Sdk.getInstance().getDal(BaseApplication.getAppContext()).getScannerHw();
            while (continueScan) {
                continueScan = scan();
            }
            cancel();
        });
    }

    /**
     * Scan
     *
     * @return Continue or not
     */
    private boolean scan() {
        try {
            // Scan and decode
            scannerHw.open();
            ScanResult scanResult = scannerHw.read(5000);
            LogUtils.d(TAG, "Scan done.");
            scannerHw.close();
            if (scanResult != null) {
                if (callback != null) {
                    callback.success(scanResult.getContent());
                }
                LogUtils.d(TAG, "Format: " + scanResult.getFormat());
                return false;
            }
            return true;
        } catch (ScannerHwDevException e) {
            if (e.getErrCode() == EScannerHwDevException.SCANNER_HW_ERR_USER_STOP_OP.getErrCodeFromBasement()) {
                LogUtils.e(TAG, "Scanner cancel");
            } else if (e.getErrCode() == EScannerHwDevException.SCANNER_HW_ERR_EXECUTE.getErrCodeFromBasement()) {
                LogUtils.e(TAG, "Stop or close scanner error");
            } else if (e.getErrCode() == EScannerHwDevException.SCANNER_HW_ERR_NO_MODULE.getErrCodeFromBasement()
                    || e.getErrCode() == EScannerHwDevException.DEVICES_ERR_NO_SUPPORT.getErrCodeFromBasement()
                    || e.getErrCode() == EScannerHwDevException.SERVICE_NOT_AVAILABLE.getErrCodeFromBasement()) {
                LogUtils.e(TAG, "Device not support scannerHw");
            } else if (e.getErrCode() == EScannerHwDevException.SCANNER_HW_ERR_OUT_OF_TIME.getErrCodeFromBasement()) {
                LogUtils.e(TAG, "Scan time out, continue scan");
                return true;
            } else {
                LogUtils.e(TAG, "", e);
            }
            if (callback != null) {
                callback.failed(e);
            }
            return false;
        } catch (Exception e) {
            LogUtils.e(TAG, "", e);
            return false;
        }
    }

    @Override
    public void cancel() {
        continueScan = false;
        callback = null;

        if (scannerHw != null) {
            try {
                scannerHw.stop();
            } catch (Exception e) {
                LogUtils.e(TAG, "", e);
            }

            try {
                scannerHw.close();
            } catch (Exception e) {
                LogUtils.e(TAG, "", e);
            }

            scannerHw = null;
        }
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void setSurfaceHolder(SurfaceHolder holder) {
        // ScannerHwImpl not support preview, so ignore this holder
    }
}
