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
 * Date	                Author	               Action
 * 20210615 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.emv.pin;

import androidx.annotation.Nullable;
import com.pax.bizlib.params.ParamHelper;
import com.pax.bizlib.ped.PedHelper;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IPed;
import com.pax.dal.entity.EKeyCode;
import com.pax.dal.entity.EPedKeyType;
import com.pax.dal.entity.EPinBlockMode;
import com.pax.dal.exceptions.PedDevException;
import com.pax.emvservice.export.exceptions.PinException;
import com.pax.emvservice.export.pin.PinInputCallback;
import com.pax.poslib.utils.PosDeviceUtils;

public class PinService {
    /**
     * The online PIN key at {@link PosDeviceUtils#INDEX_AES_PEK} (0x01) is an AES key ({@code AES_TPK},
     * written fresh per sale by {@code PaxEmvBehavior#provisionOnlinePinKey}), and the host
     * decrypts the PIN block with AES — the old app got a 16-byte AES block from EasyLink
     * ({@code TransRequest.setPinBlocEncryptType(AES)}). The 3DES {@link EPinBlockMode#ISO9564_0}
     * call used before read a different, 3DES key slot and returned the same 8-byte block every
     * sale, which the host couldn't decrypt ("HSM command error").
     *
     * <p><b>Temporary probe — the AES/ISO 9564 format 4 call isn't known yet.</b> {@link EPinBlockMode}
     * stops at {@code 0x03} in this SDK version and PAX's docs weren't reachable. On a real A920
     * (PEK at 0x01), the 5-argument byte-mode {@code getPinBlock} returned {@link #PED_ERR_GENERAL}
     * for 0x04/0x10/0x20/0x30 and {@link #PED_ERR_NO_KEY} for 0x05. The 6-argument overload with
     * its extra {@code int} set to the {@code AES_TPK} key type gave {@link #PED_ERR_NO_KEY} for
     * 0x05/0x00 and {@link #PED_ERR_GENERAL} for 0x04. This sweeps more modes on the 6-argument
     * call ({@code PaxEmvBehavior} logs whether the AES key is really in the slot via its KCV).
     * A miss ({@link #isProbeMiss}) moves on; cancel, timeout
     * or anything else stops. The log line names the call that worked — once known, collapse this
     * back to that single call.
     */
    private static final byte[] FIVE_ARG_MODES = {0x05};
    private static final byte[] SIX_ARG_AES_MODES = {0x05, 0x04, 0x00, 0x01, 0x02, 0x03, 0x10, 0x20};
    private static final int AES_TPK_KEY_TYPE = EPedKeyType.AES_TPK.getPedkeyType();

    /** {@code EPedDevException.PED_ERROR} ("ped error"). */
    private static final int PED_ERR_GENERAL = 20;
    /** {@code EPedDevException.PED_ERR_NO_KEY} ("Key does not exist"). */
    private static final int PED_ERR_NO_KEY = 1;
    /** {@code DEVICES_ERR_INVALID_ARGUMENT} / {@code DEVICES_ERR_NO_SUPPORT}. */
    private static final int ERR_INVALID_ARGUMENT = 98;
    private static final int ERR_NO_SUPPORT = 100;
    private static final int PIN_TIMEOUT_MS = 60 * 1000;

    private static final String TAG = "PinService";

    private PinInputCallback.Callback pedInputPinListener;
    private final IPed.IPedInputPinListener listener = new IPed.IPedInputPinListener() {
        @Override
        public void onKeyEvent(EKeyCode eKeyCode) {
            if (pedInputPinListener != null){
                pedInputPinListener.keyEvent(ConvertUtils.enumValue(PinInputCallback.EKeyCode.class, eKeyCode.name()));
            }
        }
    };
    /**
     * Gets encrypted PinData
     * @param panBlock panBlock
     * @param supportBypass supportBypass
     * @param landscape landscape
     * @return encrypted PinData
     * @throws PinException PinException
     */
    public byte[] getEncryptedPinData(String panBlock, boolean supportBypass, boolean landscape) throws PinException {
        try {
            IPed ped = PedHelper.getPed();
            String pinLen = "4,5,6,7,8,9,10,11,12";
            if (supportBypass) {
                pinLen = "0," + pinLen;
            }
            //外置TpyeA协议只需设置最小、最大长度
            if (ParamHelper.isExternalTypeAPed()){
                pinLen = "4,12";
            }
            if (ParamHelper.isInternalPed()){
                ped.setKeyboardLayoutLandscape(landscape);//设置密码键盘横向显示。仅支持EPedType.INTERNAL 类型。
            }

            byte[] pan = panBlock.getBytes();
            PedDevException lastMiss = null;
            for (byte mode : FIVE_ARG_MODES) {
                String call = String.format("getPinBlock(5 args, mode 0x%02X)", mode);
                try {
                    return logAccepted(call, ped.getPinBlock(PosDeviceUtils.INDEX_AES_PEK, pinLen,
                            pan, mode, PIN_TIMEOUT_MS));
                } catch (PedDevException e) {
                    lastMiss = logMissOrThrow(call, e);
                }
            }
            for (byte mode : SIX_ARG_AES_MODES) {
                String call = String.format("getPinBlock(6 args, mode 0x%02X, AES_TPK=%d)",
                        mode, AES_TPK_KEY_TYPE);
                try {
                    return logAccepted(call, ped.getPinBlock(PosDeviceUtils.INDEX_AES_PEK, pinLen,
                            pan, mode, PIN_TIMEOUT_MS, AES_TPK_KEY_TYPE));
                } catch (PedDevException e) {
                    lastMiss = logMissOrThrow(call, e);
                }
            }
            throw lastMiss;

        }catch (PedDevException e) {
            throw new PinException(String.valueOf(e.getErrCode()),e.getErrMsg());
        }
    }

    private static byte[] logAccepted(String call, byte[] pinBlock) {
        LogUtils.i(TAG, call + " accepted, block length "
                + (pinBlock == null ? 0 : pinBlock.length) + " bytes");
        return pinBlock;
    }

    private static PedDevException logMissOrThrow(String call, PedDevException e) throws PedDevException {
        if (!isProbeMiss(e)) {
            throw e;
        }
        LogUtils.w(TAG, call + " rejected: " + e.getErrCode() + " " + e.getErrMsg());
        return e;
    }

    private static boolean isProbeMiss(PedDevException e) {
        int code = e.getErrCode();
        return code == PED_ERR_GENERAL || code == PED_ERR_NO_KEY
                || code == ERR_INVALID_ARGUMENT || code == ERR_NO_SUPPORT;
    }

    /**
     * input pin callback(not pci mode)
     *
     * @param pedInputPinListener pedInputPinListener
     */
    public void setInputPinListener(@Nullable PinInputCallback.Callback pedInputPinListener) {
        IPed ped = PedHelper.getPed();
        if (pedInputPinListener == null){
            ped.setInputPinListener(null);
            this.pedInputPinListener = null;
            return;
        }
        this.pedInputPinListener = pedInputPinListener;
        ped.setInputPinListener(listener);
    }
}
