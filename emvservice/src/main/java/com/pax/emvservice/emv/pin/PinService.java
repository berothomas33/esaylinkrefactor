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
import com.pax.dal.entity.EPinBlockMode;
import com.pax.dal.exceptions.PedDevException;
import com.pax.emvservice.export.exceptions.PinException;
import com.pax.emvservice.export.pin.PinInputCallback;
import com.pax.poslib.utils.PosDeviceUtils;

public class PinService {
    /**
     * The online PIN key at {@link PosDeviceUtils#INDEX_TPK} is an AES key ({@code AES_TPK},
     * written fresh per sale by {@code PaxEmvBehavior#provisionOnlinePinKey}), and the host
     * decrypts the PIN block with AES — the old app got a 16-byte AES block from EasyLink
     * ({@code TransRequest.setPinBlocEncryptType(AES)}). The 3DES {@link EPinBlockMode#ISO9564_0}
     * call used before read a different, 3DES key slot and returned the same 8-byte block every
     * sale, which the host couldn't decrypt ("HSM command error").
     *
     * <p><b>Unconfirmed mode byte:</b> {@link EPinBlockMode} stops at {@code 0x03} (HK EPS) in this
     * SDK version and PAX's docs weren't reachable, so the byte for ISO 9564 format 4 isn't known.
     * {@code 0x04} was tried on a real A920 and rejected with {@link #PED_ERR_GENERAL}. These
     * candidates are tried in order: a {@link #PED_ERR_GENERAL} rejection moves on to the next one,
     * any other error (cancel, timeout, missing key) stops immediately. The log line says which byte
     * worked — once known, collapse this back to that single value.
     */
    private static final byte[] AES_PIN_BLOCK_MODE_CANDIDATES = {0x10, 0x20, 0x30, 0x05};

    /** {@code EPedDevException.PED_ERROR} ("ped error") — what the PED returned for mode 0x04. */
    private static final int PED_ERR_GENERAL = 20;

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

            PedDevException lastRejection = null;
            for (byte mode : AES_PIN_BLOCK_MODE_CANDIDATES) {
                try {
                    byte[] pinBlock = ped.getPinBlock(PosDeviceUtils.INDEX_TPK, pinLen,
                            panBlock.getBytes(), mode, 60 * 1000);
                    LogUtils.i(TAG, String.format("AES PIN block mode 0x%02X accepted, block length %d bytes",
                            mode, pinBlock == null ? 0 : pinBlock.length));
                    return pinBlock;
                } catch (PedDevException e) {
                    if (e.getErrCode() != PED_ERR_GENERAL) {
                        throw e;
                    }
                    LogUtils.w(TAG, String.format("AES PIN block mode 0x%02X rejected: %d %s",
                            mode, e.getErrCode(), e.getErrMsg()));
                    lastRejection = e;
                }
            }
            throw lastRejection;

        }catch (PedDevException e) {
            throw new PinException(String.valueOf(e.getErrCode()),e.getErrMsg());
        }
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
