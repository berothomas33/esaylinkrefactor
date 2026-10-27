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

import android.view.View;
import com.pax.dal.IPed;
import com.pax.dal.entity.DUKPTResult;
import com.pax.dal.entity.EAesCheckMode;
import com.pax.dal.entity.EAlgorithmType;
import com.pax.dal.entity.ECheckMode;
import com.pax.dal.entity.ECryptOperate;
import com.pax.dal.entity.ECryptOpt;
import com.pax.dal.entity.EDUKPTDesMode;
import com.pax.dal.entity.EDUKPTMacMode;
import com.pax.dal.entity.EDUKPTPinMode;
import com.pax.dal.entity.EFuncKeyMode;
import com.pax.dal.entity.EIdKeycCalcMode;
import com.pax.dal.entity.EPedDesMode;
import com.pax.dal.entity.EPedKeyType;
import com.pax.dal.entity.EPedMacMode;
import com.pax.dal.entity.EPinBlockMode;
import com.pax.dal.entity.EUartPort;
import com.pax.dal.entity.KeyInfo;
import com.pax.dal.entity.RSAKeyInfo;
import com.pax.dal.entity.RSAPinKey;
import com.pax.dal.entity.RSARecoverInfo;
import com.pax.dal.entity.SM2KeyPair;
import com.pax.dal.exceptions.PedDevException;
import java.util.LinkedHashMap;
/**
 * neptune IPed
 */
class DemoPed implements IPed {
    DemoPed() {
        //do nothing
    }

    @Override
    public void setInputPinListener(IPedInputPinListener iPedInputPinListener) {
        //do nothing
    }

    @Override
    public void writeKey(EPedKeyType ePedKeyType, byte b, EPedKeyType ePedKeyType1, byte b1, byte[] bytes, ECheckMode eCheckMode, byte[] bytes1) throws PedDevException {
        //do nothing
    }

    @Override
    public void writeTIK(byte b, byte b1, byte[] bytes, byte[] bytes1, ECheckMode eCheckMode, byte[] bytes2) throws PedDevException {
        //do nothing
    }

    @Override
    public byte[] getPinBlock(byte b, String s, byte[] bytes, EPinBlockMode ePinBlockMode, int i) throws PedDevException {
        return new byte[]{0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11};
    }

    @Override
    public byte[] getMac(byte b, byte[] bytes, EPedMacMode ePedMacMode) throws PedDevException {
        return new byte[]{0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11};
    }

    @Override
    public byte[] calcDes(byte b, byte[] bytes, EPedDesMode ePedDesMode) throws PedDevException {
        return new byte[bytes.length];
    }

    @Override
    public byte[] calcDes(byte b, byte[] bytes, byte[] bytes1, byte b1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public DUKPTResult getDUKPTPin(byte b, String s, byte[] bytes, EDUKPTPinMode edukptPinMode, int i) throws PedDevException {
        return null;
    }

    @Override
    public DUKPTResult getDUKPTMac(byte b, byte[] bytes, EDUKPTMacMode edukptMacMode) throws PedDevException {
        return null;
    }

    @Override
    public byte[] getKCV(EPedKeyType ePedKeyType, byte b, byte b1, byte[] bytes) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void writeKeyVar(EPedKeyType ePedKeyType, byte b, byte b1, byte[] bytes, ECheckMode eCheckMode, byte[] bytes1) throws PedDevException {
        //do nothing
    }

    @Override
    public String getVersion() throws PedDevException {
        return "1.0";
    }

    @Override
    public boolean erase() throws PedDevException {
        return true;
    }

    @Override
    public void setIntervalTime(int i, int i1) throws PedDevException {
        //do nothing
    }

    @Override
    public void setFunctionKey(EFuncKeyMode eFuncKeyMode) throws PedDevException {
        //do nothing
    }

    @Override
    public void writeRSAKey(byte b, RSAKeyInfo rsaKeyInfo) throws PedDevException {
        //do nothing
    }

    @Override
    public RSARecoverInfo RSARecover(byte b, byte[] bytes) throws PedDevException {
        return null;
    }

    @Override
    public DUKPTResult calcDUKPTDes(byte b, byte b1, byte[] bytes, byte[] bytes1, EDUKPTDesMode edukptDesMode) throws PedDevException {
        return null;
    }

    @Override
    public byte[] getDUKPTKsn(byte b) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void incDUKPTKsn(byte b) throws PedDevException {
        //do nothing
    }

    @Override
    public byte[] verifyPlainPin(byte b, String s, byte b1, int i) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] verifyCipherPin(byte b, String s, RSAPinKey rsaPinKey, byte b1, int i) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void setExMode(int i) {
        //do nothing
    }

    @Override
    public void clearScreen() throws PedDevException {
        //do nothing
    }

    @Override
    public String inputStr(byte b, byte b1, byte b2, int i) throws PedDevException {
        return null;
    }

    @Override
    public void showStr(byte b, byte b1, String s) throws PedDevException {
        //do nothing
    }

    @Override
    public String getSN() throws PedDevException {
        return null;
    }

    @Override
    public void showInputBox(boolean b, String s) throws PedDevException {
        //do nothing
    }

    @Override
    public SM2KeyPair genSM2KeyPair(int i) throws PedDevException {
        return null;
    }

    @Override
    public void writeSM2CipherKey(EPedKeyType ePedKeyType, byte b, EPedKeyType ePedKeyType1, byte b1, byte[] bytes) throws PedDevException {
        //do nothing
    }

    @Override
    public void writeSM2Key(byte b, EPedKeyType ePedKeyType, byte[] bytes) throws PedDevException {
        //do nothing
    }

    @Override
    public byte[] SM2Recover(byte b, byte[] bytes, ECryptOperate eCryptOperate) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] SM2Sign(byte b, byte b1, byte[] bytes, byte[] bytes1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void SM2Verify(byte b, byte[] bytes, byte[] bytes1, byte[] bytes2) throws PedDevException {
        //do nothing
    }

    @Override
    public byte[] SM3(byte[] bytes, byte b) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] SM4(byte b, byte[] bytes, byte[] bytes1, ECryptOperate eCryptOperate, ECryptOpt eCryptOpt) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] getMacSM(byte b, byte[] bytes, byte[] bytes1, byte b1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] getPinBlockSM4(byte b, String s, byte[] bytes, EPinBlockMode ePinBlockMode, int i) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void cancelInput() throws PedDevException {
        //do nothing
    }

    @Override
    public void setAmount(String s) throws PedDevException {
        //do nothing
    }

    @Override
    public byte[] idKeyCalc(byte b, byte[] bytes, byte[] bytes1, EIdKeycCalcMode eIdKeycCalcMode) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void setKeyboardLayoutLandscape(boolean b) throws PedDevException {
        //do nothing
    }

    @Override
    public byte[] setKeyBoardLayout(boolean b, String s) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void writeAesKey(EPedKeyType ePedKeyType, byte b, byte b1, byte[] bytes, EAesCheckMode eAesCheckMode, byte[] bytes1) throws PedDevException {
        //do nothing
    }

    @Override
    public void setKeyboardRandom(boolean b) throws PedDevException {
        //do nothing
    }

    @Override
    public byte[] calcAes(byte b, byte[] bytes, byte[] bytes1, ECryptOperate eCryptOperate, ECryptOpt eCryptOpt) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void genRSAKey(byte b, byte b1, short i, byte b2) throws PedDevException {
        //do nothing
    }

    @Override
    public void setPort(EUartPort eUartPort) {
        //do nothing
    }

    @Override
    public byte[] getPinBlock(byte b, String s, byte[] bytes, byte b1, int i) throws PedDevException {
        return new byte[0];
    }

    @Override
    public RSAKeyInfo readRSAKey(byte b) throws PedDevException {
        return null;
    }

    @Override
    public void setFunctionKey(byte b) throws PedDevException {
        //do nothing
    }

    @Override
    public DUKPTResult getDUKPTPin(byte b, String s, byte[] bytes, Boolean aBoolean, String s1, String s2, int i) throws PedDevException {
        return null;
    }

    @Override
    public void setKeyBoardType(int i) throws PedDevException {
        // do nothing
    }

    @Override
    public int getKeyBoardType() throws PedDevException {
        return 0;
    }

    @Override
    public byte[] getPinBlock(byte b, String s, byte[] bytes, byte b1, int i, int i1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] verifyPlainPin(byte b, String s, byte b1, int i, int i1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] verifyCipherPin(byte b, String s, RSAPinKey rsaPinKey, byte b1, int i, int i1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] setKeyBoardLayout(boolean b, LinkedHashMap<View, String> linkedHashMap) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void writeTIK(byte b, byte b1, byte b2, byte[] bytes) throws PedDevException {
        // do nothing
    }

    @Override
    public void writeKeyEx(EPedKeyType ePedKeyType, byte b, EPedKeyType ePedKeyType1, byte b1, byte[] bytes, ECheckMode eCheckMode, byte[] bytes1, byte[] bytes2, byte b2) throws PedDevException {
        // do nothing
    }

    @Override
    public byte[] readPaxCA(byte b) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void writeAesKey(byte b, byte b1, byte b2, byte b3, byte[] bytes, EAesCheckMode eAesCheckMode, byte[] bytes1) throws PedDevException {
        // do nothing
    }

    @Override
    public DUKPTResult calcDUKPTData(byte b, byte b1, byte[] bytes, byte[] bytes1, byte b2) throws PedDevException {
        return null;
    }

    @Override
    public DUKPTResult getDUKPTMac(byte b, byte[] bytes, byte b1) throws PedDevException {
        return null;
    }

    @Override
    public void eraseKeyEx(byte b) throws PedDevException {
        // do nothing
    }

    @Override
    public byte[] challengeWICKey(byte b, byte b1, byte[] bytes, byte[] bytes1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void inputPin(String s, long l, byte b) throws PedDevException {
        // do nothing
    }

    @Override
    public byte[] pinEndGetPinBlock(byte b, byte[] bytes, byte b1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public DUKPTResult pinEndGetDukptPin(byte b, byte[] bytes, byte b1) throws PedDevException {
        return null;
    }

    @Override
    public DUKPTResult pinEndGetAesDukptPin(byte b, byte[] bytes, EAlgorithmType eAlgorithmType, byte b1) throws PedDevException {
        return null;
    }

    @Override
    public byte[] pinEndVerifyPlainPin(byte b, byte b1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] pinEndVerifyCipherPin(byte b, RSAPinKey rsaPinKey, byte b1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void setKeyboard(byte b) throws PedDevException {
        // do nothing
    }

    @Override
    public void eraseKey(byte b, byte b1) throws PedDevException {
        // do nothing
    }

    @Override
    public void writeTR31Key(byte b, byte b1, byte b2, byte[] bytes) throws PedDevException {
        // do nothing
    }

    @Override
    public String genCSR(byte b, byte b1, String s) throws PedDevException {
        return null;
    }

    @Override
    public byte[] calcHMAC(int i, byte[] bytes, int i1) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void writeSaltKey(byte[] bytes) throws PedDevException {
        // do nothing
    }

    @Override
    public void writeAesDUKPTTIK(byte b, byte b1, byte[] bytes, byte[] bytes1, byte b2,
            byte[] bytes2) throws PedDevException {
        //do nothing
    }

    @Override
    public DUKPTResult getAesDUKPTPin(byte b, String s, byte[] bytes, EAlgorithmType eAlgorithmType,
            byte b1, long l) throws PedDevException {
        return null;
    }

    @Override
    public DUKPTResult calcAesDUKPTData(byte b, byte b1, byte[] bytes, byte[] bytes1,
            EAlgorithmType eAlgorithmType, byte b2) throws PedDevException {
        return null;
    }

    @Override
    public DUKPTResult getAesDUKPTMac(byte b, byte[] bytes, EAlgorithmType eAlgorithmType, byte b1)
            throws PedDevException {
        return null;
    }

    @Override
    public byte[] getAesDUKPTKsn(byte b) throws PedDevException {
        return new byte[0];
    }

    @Override
    public void incAesDUKPTKsn(byte b) throws PedDevException {
        //do nothing
    }

    @Override
    public void writeCipherKey(byte b, byte b1, byte[] bytes, byte[] bytes1, byte b2)
            throws PedDevException {
        //do nothing
    }

    @Override
    public KeyInfo queryKeyInfo(byte b, byte b1) throws PedDevException {
        return null;
    }

    @Override
    public void setDoubleTapKeyboardLanguage(byte b) throws PedDevException {
        // do nothing
    }

    @Override
    public void m1AuthorityDiversified(byte b, byte b1, byte b2, byte b3, byte[] bytes)
            throws PedDevException {
        // do nothing
    }

    @Override
    public byte[] calcDesfireAuth(byte b, byte b1, byte[] bytes, byte[] bytes1, byte[] bytes2,
            byte b2) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] readKeyInfo(byte b, byte b1, byte b2) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] keyCalcMac(byte b, byte b1, byte[] bytes, byte b2) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] paxCARecover(byte b, byte b1, byte[] bytes) throws PedDevException {
        return new byte[0];
    }

    @Override
    public byte[] getMacAes(byte b, byte[] bytes, byte b1) throws PedDevException {
        return new byte[0];
    }
}
