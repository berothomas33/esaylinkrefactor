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
 * 2021/08/09                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.poslib.model;

import android.os.Build;
import com.pax.commonlib.utils.LogUtils;
import com.pax.poslib.model.a.A35;
import com.pax.poslib.model.a.A60;
import com.pax.poslib.model.a.A800;
import com.pax.poslib.model.a.A920;
import com.pax.poslib.model.a.A920Mini;
import com.pax.poslib.model.a.A920Pro;
import com.pax.poslib.model.aries.Aries6;
import com.pax.poslib.model.aries.Aries8;

/**
 * Device Information
 */
public class ModelInfo implements IModel {
    private static final String TAG = "ModelInfo";
    private IModel model;

    private ModelInfo() {
        model = getModel();
    }

    public static ModelInfo getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final ModelInfo INSTANCE = new ModelInfo();
    }

    public void buildCache() {
        model = new ModelCache(getModel());
    }

    private IModel getModel() {
        String model = Build.MODEL.toUpperCase();
        LogUtils.d(TAG, "model: " + model);
        switch (model) {
            case "A920":
                return new A920();
            case "A920PRO":
                return new A920Pro();
            case "A920MINI":
                return new A920Mini();
            case "A800":
                return new A800();
            case "A35":
                return new A35();
            case "A60":
                return new A60();
            case "ARIES6":
                return new Aries6();
            case "ARIES8":
                return new Aries8();
            default:
                return new DefaultModel();
        }
    }

    @Override
    public boolean isSupportMag() {
        return model.isSupportMag();
    }

    @Override
    public boolean isSupportIcc() {
        return model.isSupportIcc();
    }

    @Override
    public boolean isSupportPicc() {
        return model.isSupportPicc();
    }

    @Override
    public boolean isMagIccConflict() {
        return model.isMagIccConflict();
    }

    @Override
    public boolean isSupportCamera() {
        return model.isSupportCamera();
    }

    @Override
    public int getCameraId() {
        return model.getCameraId();
    }

    @Override
    public boolean isSupportScannerHw() {
        return model.isSupportScannerHw();
    }

    @Override
    public boolean isSupportScanButton() {
        return model.isSupportScanButton();
    }

    @Override
    public boolean isSupportWiFi() {
        return model.isSupportWiFi();
    }

    @Override
    public boolean isSupportLAN() {
        return model.isSupportLAN();
    }

    @Override
    public boolean isSupportKeyboard() {
        return model.isSupportKeyboard();
    }

    @Override
    public boolean isSupportPrinter() {
        return model.isSupportPrinter();
    }

    @Override
    public String getAndroidFwVersion() {
        return model.getAndroidFwVersion();
    }

    @Override
    public String getBootVersion() {
        return model.getBootVersion();
    }

    @Override
    public String getCustomerSN() {
        return model.getCustomerSN();
    }

    @Override
    public String getSN() {
        return model.getSN();
    }

    @Override
    public String getMainPCBVersion() {
        return model.getMainPCBVersion();
    }

    @Override
    public String getExtendedPCBConfig() {
        return model.getExtendedPCBConfig();
    }

    @Override
    public String getMagReaderPCBConfig() {
        return model.getMagReaderPCBConfig();
    }

    @Override
    public String getInterfacePCBConfig() {
        return model.getInterfacePCBConfig();
    }

    @Override
    public String getModelUppercase() {
        return model.getModelUppercase();
    }

    @Override
    public String getTerminalModel() {
        return model.getTerminalModel();
    }

    @Override
    public String getMonitorMajorVersion() {
        return model.getMonitorMajorVersion();
    }

    @Override
    public String getMonitorVersion() {
        return model.getMonitorVersion();
    }

    @Override
    public String getRFType() {
        return model.getRFType();
    }

    @Override
    public String getRPCVersion() {
        return model.getRPCVersion();
    }

    @Override
    public int getReceiptWidth() {
        return model.getReceiptWidth();
    }
}
