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

            return ped.getPinBlock(PosDeviceUtils.INDEX_TPK, pinLen, panBlock.getBytes(), EPinBlockMode.ISO9564_0, 60 * 1000);

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
