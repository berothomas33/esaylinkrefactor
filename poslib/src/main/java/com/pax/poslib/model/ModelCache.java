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
 * 2021/12/10                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.poslib.model;

/**
 * Model information cache.
 * <br>
 * Because the device information of a device is fixed, it can be cached directly to save time.
 * <br>
 * Please also note that what is cached here is whether a certain function is supported, not
 * whether a certain function is enabled. The result returned by the former is fixed, and only
 * the result returned by the latter is subject to change.
 * <br><br>
 * 因为一台设备的设备信息是固定的，所以可以直接将其缓存，节约时间。
 * <br>
 * 另外请注意，这里缓存的是是否支持某种功能，而非是否启用了某种功能。前者返回的结果是固定的，只有后者返回的结果是
 * 有可能变化的。
 */
class ModelCache implements IModel {
    private final boolean supportMag;
    private final boolean supportIcc;
    private final boolean supportPicc;
    private final boolean magIccConflict;
    private final boolean supportCamera;
    private final int cameraId;
    private final boolean supportScannerHw;
    private final boolean supportScanButton;
    private final boolean supportWiFi;
    private final boolean supportLAN;
    private final boolean supportKeyboard;
    private final boolean supportPrinter;

    private final String androidFwVersion;
    private final String bootVersion;
    private final String customerSN;
    private final String SN;
    private final String mainPCBVersion;
    private final String extendedPCBConfig;
    private final String magReaderPCBConfig;
    private final String interfacePCBConfig;
    private final String modelUppercase;
    private final String terminalModel;
    private final String monitorMajorVersion;
    private final String monitorVersion;
    private final String rfType;
    private final String rpcVersion;

    private final int receiptWidth;

    /**
     * Cache an IModel instance.
     * <br>
     * Because the device information of a device is fixed, it can be cached directly to save time.
     * <br>
     * Please also note that what is cached here is whether a certain function is supported, not
     * whether a certain function is enabled. The result returned by the former is fixed, and only
     * the result returned by the latter is subject to change.
     * <br><br>
     * 因为一台设备的设备信息是固定的，所以可以直接将其缓存，节约时间。
     * <br>
     * 另外请注意，这里缓存的是是否支持某种功能，而非是否启用了某种功能。前者返回的结果是固定的，只有后者返回的结果是
     * 有可能变化的。
     *
     * @param model Cached IModel instance
     */
    public ModelCache(IModel model) {
        supportMag = model.isSupportMag();
        supportIcc = model.isSupportIcc();
        supportPicc = model.isSupportPicc();
        magIccConflict = model.isMagIccConflict();
        supportCamera = model.isSupportCamera();
        cameraId = model.getCameraId();
        supportScannerHw = model.isSupportScannerHw();
        supportScanButton = model.isSupportScanButton();
        supportWiFi = model.isSupportWiFi();
        supportLAN = model.isSupportLAN();
        supportKeyboard = model.isSupportKeyboard();
        supportPrinter = model.isSupportPrinter();

        androidFwVersion = model.getAndroidFwVersion();
        bootVersion = model.getBootVersion();
        customerSN = model.getCustomerSN();
        SN = model.getSN();
        mainPCBVersion = model.getMainPCBVersion();
        extendedPCBConfig = model.getExtendedPCBConfig();
        magReaderPCBConfig = model.getMagReaderPCBConfig();
        interfacePCBConfig = model.getInterfacePCBConfig();
        modelUppercase = model.getModelUppercase();
        terminalModel = model.getTerminalModel();
        monitorMajorVersion = model.getMonitorMajorVersion();
        monitorVersion = model.getMonitorVersion();
        rfType = model.getRFType();
        rpcVersion = model.getRPCVersion();

        receiptWidth = model.getReceiptWidth();
    }

    @Override
    public boolean isSupportMag() {
        return supportMag;
    }

    @Override
    public boolean isSupportIcc() {
        return supportIcc;
    }

    @Override
    public boolean isSupportPicc() {
        return supportPicc;
    }

    @Override
    public boolean isMagIccConflict() {
        return magIccConflict;
    }

    @Override
    public boolean isSupportCamera() {
        return supportCamera;
    }

    @Override
    public int getCameraId() {
        return cameraId;
    }

    @Override
    public boolean isSupportScannerHw() {
        return supportScannerHw;
    }

    @Override
    public boolean isSupportScanButton() {
        return supportScanButton;
    }

    @Override
    public boolean isSupportWiFi() {
        return supportWiFi;
    }

    @Override
    public boolean isSupportLAN() {
        return supportLAN;
    }

    @Override
    public boolean isSupportKeyboard() {
        return supportKeyboard;
    }

    @Override
    public boolean isSupportPrinter() {
        return supportPrinter;
    }

    @Override
    public String getAndroidFwVersion() {
        return androidFwVersion;
    }

    @Override
    public String getBootVersion() {
        return bootVersion;
    }

    @Override
    public String getCustomerSN() {
        return customerSN;
    }

    @Override
    public String getSN() {
        return SN;
    }

    @Override
    public String getMainPCBVersion() {
        return mainPCBVersion;
    }

    @Override
    public String getExtendedPCBConfig() {
        return extendedPCBConfig;
    }

    @Override
    public String getMagReaderPCBConfig() {
        return magReaderPCBConfig;
    }

    @Override
    public String getInterfacePCBConfig() {
        return interfacePCBConfig;
    }

    @Override
    public String getModelUppercase() {
        return modelUppercase;
    }

    @Override
    public String getTerminalModel() {
        return terminalModel;
    }

    @Override
    public String getMonitorMajorVersion() {
        return monitorMajorVersion;
    }

    @Override
    public String getMonitorVersion() {
        return monitorVersion;
    }

    @Override
    public String getRFType() {
        return rfType;
    }

    @Override
    public String getRPCVersion() {
        return rpcVersion;
    }

    @Override
    public int getReceiptWidth() {
        return receiptWidth;
    }
}
