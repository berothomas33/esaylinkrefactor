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

/**
 * Device Information
 */
public interface IModel {
    /**
     * Does this device support MAG
     *
     * @return Does this device support MAG. If supported, return true.
     */
    boolean isSupportMag();

    /**
     * Does this device support ICC
     *
     * @return Does this device support ICC. If supported, return true.
     */
    boolean isSupportIcc();

    /**
     * Does this device support PICC
     *
     * @return Does this device support PICC. If supported, return true.
     */
    boolean isSupportPicc();

    /**
     * Will there be conflicts between MAG and ICC in this device
     *
     * @return Will there be conflicts between MAG and ICC in this device. If MAG and ICC
     * conflict, return true.
     */
    boolean isMagIccConflict();

    /**
     * Does this device support camera
     *
     * @return Does this device support camera. If supported, return true.
     */
    boolean isSupportCamera();

    /**
     * Get camera ID
     *
     * @return Camera ID
     */
    int getCameraId();

    /**
     * Does this device support scanner hardware
     *
     * @return Does this device support scanner hardware. If supported, return true.
     */
    boolean isSupportScannerHw();

    /**
     * Does this device support scan button
     *
     * @return Does this device support scan button. If supported, return true.
     */
    boolean isSupportScanButton();

    /**
     * Does this device support Wi-Fi
     *
     * @return Does this device support Wi-Fi. If supported, return true.
     */
    boolean isSupportWiFi();

    /**
     * Does this device support LAN
     *
     * @return Does this device support LAN. If supported, return true.
     */
    boolean isSupportLAN();

    // TODO: Add a method for judging whether the device supports the mobile cellular network

    boolean isSupportKeyboard();

    /**
     * Does this device support Printer
     *
     * @return Does this device support printer. If supported, return true.
     */
    boolean isSupportPrinter();

    /**
     * Get Android Firmware Version
     *
     * @return Android Firmware Version (ETermInfoKey.AP_VER)
     */
    String getAndroidFwVersion();

    /**
     * Get Boot Version (ascending from 1)
     *
     * @return Boot Version (ETermInfoKey.BIOS_VER)
     */
    String getBootVersion();

    /**
     * Get Customer Serial Number
     *
     * @return Customer Serial Number (ETermInfoKey.CSN)
     */
    String getCustomerSN();

    /**
     * Get Terminal Serial Number
     *
     * @return Terminal Serial Number (ETermInfoKey.SN)
     */
    String getSN();

    /**
     * Get Main PCB Hardware Version
     *
     * @return Main PCB Hardware Version (ETermInfoKey.MAINB_VER)
     */
    String getMainPCBVersion();

    /**
     * Get Extended PCB Configuration Information
     *
     * @return Extended PCB Configuration Information (ETermInfoKey.EXTB_CFG)
     */
    String getExtendedPCBConfig();

    /**
     * Get MagCard Reader PCB Configuration Information
     *
     * @return MagCard Reader PCB Configuration Information (ETermInfoKey.MAGB_CFG)
     */
    String getMagReaderPCBConfig();

    /**
     * Get Interface PCB Configuration Information
     *
     * @return Interface PCB Configuration Information (ETermInfoKey.PORTB_CFG)
     */
    String getInterfacePCBConfig();

    /**
     * Get Terminal Model (Uppercase)
     *
     * @return Terminal Model (ETermInfoKey.MODEL)
     */
    String getModelUppercase();

    /**
     * Get Terminal Model
     *
     * @return Terminal Model (ETermInfoKey.MODEL_ORIG)
     */
    String getTerminalModel();

    /**
     * Get 'monitor major version(ascending from 1);monitor minor version(ascending from 0)'
     *
     * @return Monitor Version (ETermInfoKey.MON_VER)
     */
    String getMonitorMajorVersion();

    /**
     * Get Monitor Version
     *
     * @return Monitor Version (ETermInfoKey.SP_VER)
     */
    String getMonitorVersion();

    /**
     * Get RF Chip Type
     *
     * @return RF Chip Type (ETermInfoKey.RF_TYPE)
     */
    String getRFType();

    /**
     * Get 'rpc app major version number (ascending from 1);rpc app minor version number
     * (ascending from 0)'
     *
     * @return RPC Version (ETermInfoKey.RPC_VER)
     */
    String getRPCVersion();

    /**
     * Get receipt width (pixel unit)
     *
     * @return Receipt width
     */
    int getReceiptWidth();
}
