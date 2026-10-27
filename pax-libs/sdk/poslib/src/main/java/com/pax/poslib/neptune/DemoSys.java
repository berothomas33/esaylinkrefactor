/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2019-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                  Author	                 Action
 * 20190108  	         Kim.L                   Create
 * ===========================================================================================
 */
package com.pax.poslib.neptune;

import android.content.Context;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.SystemClock;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.ThreadPoolManager;
import com.pax.dal.ISys;
import com.pax.dal.entity.ASCaller;
import com.pax.dal.entity.BaseInfo;
import com.pax.dal.entity.EBeepMode;
import com.pax.dal.entity.ENavigationKey;
import com.pax.dal.entity.ETermInfoKey;
import com.pax.dal.entity.ETouchMode;
import com.pax.dal.entity.NtpServerParam;
import com.pax.dal.entity.PosMenu;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static android.content.Context.AUDIO_SERVICE;
/**
 * neptune ISys
 */
class DemoSys implements ISys {

    private Context context;
    private Map<ETermInfoKey, String> infoKeyStringMap = new HashMap<>();

    DemoSys(Context context) {
        this.context = context;
        infoKeyStringMap.put(ETermInfoKey.SN, "12345678");
    }

    @Override
    public boolean beep(EBeepMode eBeepMode, final int i) {
        ThreadPoolManager.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                AudioManager am = (AudioManager) BaseApplication.getAppContext().getSystemService(AUDIO_SERVICE);
                am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
                SystemClock.sleep(i);
            }
        });
        return true;
    }

    @Override
    public Map<ETermInfoKey, String> getTermInfo() {
        return infoKeyStringMap;
    }

    @Override
    public byte[] getRandom(int i) {
        return new byte[0];
    }

    @Override
    public String getDevInterfaceVer() {
        return null;
    }

    @Override
    public boolean checkPermission(String s, String s1) {
        return false;
    }

    @Override
    public void showNavigationBar(boolean b) {
        //do nothing
    }

    @Override
    public void enableNavigationBar(boolean b) {
        //do nothing
    }

    @Override
    public void enableNavigationKey(ENavigationKey eNavigationKey, boolean b) {
        //do nothing
    }

    @Override
    public boolean isNavigationBarVisible() {
        return true;
    }

    @Override
    public boolean isNavigationBarEnabled() {
        return true;
    }

    @Override
    public boolean isNavigationKeyEnabled(ENavigationKey eNavigationKey) {
        return false;
    }

    @Override
    public void showStatusBar(boolean b) {
        //do nothing
    }

    @Override
    public void enableStatusBar(boolean b) {
        //do nothing
    }

    @Override
    public boolean isStatusBarEnabled() {
        return true;
    }

    @Override
    public boolean isStatusBarVisible() {
        return true;
    }

    @Override
    public void resetStatusBar() {
        //do nothing
    }

    @Override
    public void enablePowerKey(boolean b) {
        //do nothing
    }

    @Override
    public boolean isPowerKeyEnabled() {
        return true;
    }

    @Override
    public void setSettingsNeedPassword(boolean b) {
        //do nothing
    }

    @Override
    public void reboot() {
        //do nothing
    }

    @Override
    public void shutdown() {
        //do nothing
    }

    @Override
    public void switchTouchMode(ETouchMode eTouchMode) {
        //do nothing
    }

    @Override
    public String getDate() {
        return null;
    }

    @Override
    public void setDate(String s) {
        //do nothing
    }

    @Override
    public void writeCSN(String s) {
        //do nothing
    }

    @Override
    public void setScreenBrightness(int i) {
        // do nothing
    }

    @Override
    public boolean switchPrintService(Context context, String s, String s1, boolean b) {
        return false;
    }

    @Override
    public void disablePosMenu(Map<PosMenu, Boolean> map) {
        // do nothing
    }

    @Override
    public void setScreenOffTime(int i) throws Exception {
        // do nothing
    }

    @Override
    public int getScreenOffTime() throws Exception {
        return 0;
    }

    @Override
    public void setScreenSaverTime(int i) throws Exception {
        // do nothing
    }

    @Override
    public void setUsbMode(int i) throws Exception {
        // do nothing
    }

    @Override
    public void enableUsbPermissionDialog(boolean b) {
        // do nothing
    }

    @Override
    public boolean setWifiStaticIp(String s, String s1, int i, String s2, String s3, boolean b) {
        return false;
    }

    @Override
    public void addService(String s, IBinder iBinder) {
        // do nothing
    }

    @Override
    public void enableAutoTimeZone(boolean b) {
        // do nothing
    }

    @Override
    public boolean isAutoTimeZone() {
        return false;
    }

    @Override
    public void enableAutoTime(boolean b) {
        // do nothing
    }

    @Override
    public boolean isAutoTime() {
        return false;
    }

    @Override
    public void setScreenSaver(String s, String s1) {
        // do nothing
    }

    @Override
    public void setSettingsPassword(byte[] bytes, String s, ASCaller asCaller) {
        // do nothing
    }

    @Override
    public String getScreenSaver() throws Exception {
        return null;
    }

    @Override
    public void enableScreenSaver(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public void setScreenSaverActivateType(int i) throws Exception {
        // do nothing
    }

    @Override
    public void enableShutdownConfirm(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public int getUsbMode() throws Exception {
        return 0;
    }

    @Override
    public void enableVolumeKey(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public boolean isVolumeKeyEnable() throws Exception {
        return false;
    }

    @Override
    public void enableSystemOTA(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public boolean isSystemOTAEnable() throws Exception {
        return false;
    }

    @Override
    public void setScanResultMode(int i) throws Exception {
        // do nothing
    }

    @Override
    public void setSettingsNeedPassword(byte[] bytes, boolean b, ASCaller asCaller) throws Exception {
        // do nothing
    }

    @Override
    public void removeRecentTasks(List<String> list) throws Exception {
        // do nothing
    }

    @Override
    public List<String> getAppsWhitelist(byte[] bytes, ASCaller asCaller) throws Exception {
        return null;
    }

    @Override
    public void setAppsWhitelist(byte[] bytes, byte[] bytes1, ASCaller asCaller) throws Exception {
        // do nothing
    }

    @Override
    public boolean verifySign(int i, String s) throws Exception {
        return false;
    }

    @Override
    public boolean enableWiFiDHCP() throws Exception {
        return false;
    }

    @Override
    public void setLauncher(String s, String s1, boolean b) throws Exception {
        // do nothing
    }

    @Override
    public void setShortcutAction(String s) throws Exception {
        // do nothing
    }

    @Override
    public String getCustomerResVer() throws Exception {
        return null;
    }

    @Override
    public void enableLocation(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public void resetNetworkSettings() throws Exception {
        // do nothing
    }

    @Override
    public void enableApplication(String s, boolean b) throws Exception {
        // do nothing
    }

    @Override
    public void enableAuthDownload(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public String getInterceptorVersion() throws Exception {
        return null;
    }

    @Override
    public void setBootAnimation(String s) throws Exception {
        // do nothing
    }

    @Override
    public void setBootLogo(String s) throws Exception {
        // do nothing
    }

    @Override
    public void enableShortPressPowerKey(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public void enableEthernetTether(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public boolean set24Hour(boolean b) {
        return false;
    }

    @Override
    public void enableBaseUsb(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public void writeCustomerResConfig(String s) throws Exception {
        // do nothing
    }

    @Override
    public boolean turnOnWiFiHotspot(String s, String s1, int i) {
        return false;
    }

    @Override
    public boolean turnOffWiFiHotspot() {
        return false;
    }

    @Override
    public int getWiFiHotspotStatus() {
        return 0;
    }

    @Override
    public void setChargeLimit(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public boolean setNTPServerParam(NtpServerParam ntpServerParam) throws Exception {
        return false;
    }

    @Override
    public NtpServerParam getNTPServerParam() throws Exception {
        return null;
    }

    @Override
    public void enableBatterySaverPrompt(boolean b) throws Exception {
        // do nothing
    }

    @Override
    public boolean enableKeyEvent() {
        return false;
    }

    @Override
    public boolean disableKeyEvent() {
        return false;
    }

    @Override
    public boolean enableADBAndMTP(boolean b) {
        return false;
    }

    @Override
    public byte[] getTermInfoExt() {
        return new byte[0];
    }

    @Override
    public int getAppLogs(String s, String s1, String s2) {
        return 0;
    }

    @Override
    public boolean switchSimCard(int i) {
        return false;
    }

    @Override
    public String readTUSN() {
        return "";
    }

    @Override
    public int getPedMode() {
        return 0;
    }

    @Override
    public BaseInfo getBaseInfo() {
        return null;
    }

    @Override
    public int installApp(String s) {
        return 0;
    }

    @Override
    public boolean isDebug() {
        return false;
    }

    @Override
    public int uninstallApp(String s) {
        return 0;
    }

    @Override
    public int updateFirmware(String s) {
        return 0;
    }

    @Override
    public boolean isOnBase() {
        return false;
    }

    @Override
    public void beep(String s) {
        //do nothing
    }

    @Override
    public void setTimeZone(String s) {
        //do nothing
    }

    @Override
    public String getSystemLanguage() {
        return null;
    }

    @Override
    public int setSystemLanguage(Locale locale) {
        return 0;
    }

    @Override
    public void ledControl(byte b, byte b1) {
        // do nothing
    }

    @Override
    public void lightControl(byte b, byte b1) {
        // do nothing
    }

    @Override
    public String getPN() {
        return null;
    }
}
