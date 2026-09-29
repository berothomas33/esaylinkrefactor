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
     * The online PIN key at {@link PosDeviceUtils#INDEX_AES_PEK} (0x01) is an AES key ({@code AES_TPK},
     * erased and written fresh per sale by {@code PaxEmvBehavior#provisionOnlinePinKey}), and the
     * host decrypts the PIN block with AES as ISO 9564 format 4, like the old app. The 3DES
     * {@link EPinBlockMode#ISO9564_0} call used before read a different, 3DES key slot, which the
     * host couldn't decrypt ("HSM command error").
     *
     * <p>NeptuneLite {@code IPed#getPinBlock(byte, String, byte[], byte, int)}: "0x14: Using
     * AES_TPK encryption, pinblock is in ISO9564 format 4 ... When mode=0x14, DataIn is the
     * original primary account" — so the full PAN goes in as-is (not the 12-digit PAN block),
     * and the result is a 16-byte block.
     */
    private static final byte PIN_BLOCK_MODE_ISO9564_4_AES = 0x14;
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

            byte[] pinBlock = ped.getPinBlock(PosDeviceUtils.INDEX_AES_PEK, pinLen,
                    panBlock.getBytes(), PIN_BLOCK_MODE_ISO9564_4_AES, PIN_TIMEOUT_MS);
            LogUtils.i(TAG, "ISO 9564 format 4 (AES) PIN block, "
                    + (pinBlock == null ? 0 : pinBlock.length) + " bytes");
            return pinBlock;

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
