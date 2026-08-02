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

package com.pax.poslib.model;

import android.content.pm.PackageManager;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LazyInit;
import com.pax.dal.IDeviceInfo;
import com.pax.dal.entity.ETermInfoKey;
import com.pax.poslib.neptune.Sdk;
import java.util.Map;

/**
 * Default Device Information.
 */
public class DefaultModel implements IModel {
    private final LazyInit<Map<ETermInfoKey, String>> termInfoMap = LazyInit.by(() -> Sdk
            .getInstance().getDal(BaseApplication.getAppContext())
            .getSys().getTermInfo());

    private boolean isModuleSupport(int module) {
        return Sdk.getInstance()
                .getDal(BaseApplication.getAppContext())
                .getDeviceInfo()
                .getModuleSupported(module) == IDeviceInfo.ESupported.YES;
    }

    @Override
    public boolean isSupportMag() {
        return isModuleSupport(IDeviceInfo.MODULE_MAG);
    }

    @Override
    public boolean isSupportIcc() {
        return isModuleSupport(IDeviceInfo.MODULE_ICC);
    }

    @Override
    public boolean isSupportPicc() {
        return isModuleSupport(IDeviceInfo.MODULE_PICC);
    }

    @Override
    public boolean isMagIccConflict() {
        return false;
    }

    @Override
    public boolean isSupportCamera() {
        PackageManager pm = BaseApplication.getAppContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    @Override
    public int getCameraId() {
        return 0;
    }

    @Override
    public boolean isSupportScannerHw() {
        return isModuleSupport(IDeviceInfo.MODULE_SCANNER_HW);
    }

    @Override
    public boolean isSupportScanButton() {
        return false;
    }

    @Override
    public boolean isSupportWiFi() {
        return isModuleSupport(IDeviceInfo.MODULE_WIFI) || isModuleSupport(IDeviceInfo.MODULE_WIFI5G);
    }

    @Override
    public boolean isSupportLAN() {
        return isModuleSupport(IDeviceInfo.MODULE_ETHERNET);
    }

    @Override
    public boolean isSupportKeyboard() {
        return isModuleSupport(IDeviceInfo.MODULE_KEYBOARD);
    }

    @Override
    public boolean isSupportPrinter() {
        return isModuleSupport(IDeviceInfo.MODULE_PRINTER);
    }

    @Override
    public String getAndroidFwVersion() {
        return termInfoMap.get().get(ETermInfoKey.AP_VER);
    }

    @Override
    public String getBootVersion() {
        return termInfoMap.get().get(ETermInfoKey.BIOS_VER);
    }

    @Override
    public String getCustomerSN() {
        return termInfoMap.get().get(ETermInfoKey.CSN);
    }

    @Override
    public String getSN() {
        return termInfoMap.get().get(ETermInfoKey.SN);
    }

    @Override
    public String getMainPCBVersion() {
        return termInfoMap.get().get(ETermInfoKey.MAINB_VER);
    }

    @Override
    public String getExtendedPCBConfig() {
        return termInfoMap.get().get(ETermInfoKey.EXTB_CFG);
    }

    @Override
    public String getMagReaderPCBConfig() {
        return termInfoMap.get().get(ETermInfoKey.MAGB_CFG);
    }

    @Override
    public String getInterfacePCBConfig() {
        return termInfoMap.get().get(ETermInfoKey.PORTB_CFG);
    }

    @Override
    public String getModelUppercase() {
        return termInfoMap.get().get(ETermInfoKey.MODEL);
    }

    @Override
    public String getTerminalModel() {
        return termInfoMap.get().get(ETermInfoKey.MODEL_ORIG);
    }

    @Override
    public String getMonitorMajorVersion() {
        return termInfoMap.get().get(ETermInfoKey.MON_VER);
    }

    @Override
    public String getMonitorVersion() {
        return termInfoMap.get().get(ETermInfoKey.SP_VER);
    }

    @Override
    public String getRFType() {
        return termInfoMap.get().get(ETermInfoKey.RF_TYPE);
    }

    @Override
    public String getRPCVersion() {
        return termInfoMap.get().get(ETermInfoKey.RPC_VER);
    }

    @Override
    public int getReceiptWidth() {
        return EReceiptWidth.WIDTH_57_MM.getWidth();
    }
}
