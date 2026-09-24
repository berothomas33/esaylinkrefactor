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
     * <p><b>Unconfirmed value:</b> {@link EPinBlockMode} stops at {@code 0x03} (HK EPS) in this SDK
     * version and PAX's docs weren't available to check, so {@code 0x04} (ISO 9564 format 4, the
     * AES PIN block) is the next-in-sequence value. If the PED rejects it, try {@code 0x10}.
     * The right value gives a 16-byte block that changes every sale.
     */
    private static final byte PIN_BLOCK_MODE_ISO9564_4_AES = 0x04;

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

            return ped.getPinBlock(PosDeviceUtils.INDEX_TPK, pinLen, panBlock.getBytes(),
                    PIN_BLOCK_MODE_ISO9564_4_AES, 60 * 1000);

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
