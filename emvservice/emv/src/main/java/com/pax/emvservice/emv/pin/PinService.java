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
import com.pax.emvservice.export.EmvServiceConstant;
import com.pax.emvservice.export.exceptions.PinException;
import com.pax.emvservice.export.pin.IPinService;
import com.pax.emvservice.export.pin.PinInputCallback;
import com.pax.poslib.gl.convert.ConvertHelper;
import com.pax.poslib.utils.PosDeviceUtils;
import com.sankuai.waimai.router.annotation.RouterService;

@RouterService(interfaces = IPinService.class,key = EmvServiceConstant.EMVSERVICE_PIN)
public class PinService implements IPinService {
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
     * External Ped situation
     * when PCI verify offline PIN interface is not used, this parameter is output.
     * The application should return plaintext PIN in it with string ended with '\x00'.
     *
     * @param encryptPinData encryptPinData
     * @param panBlock panBlock
     * @return plaintext PIN in it with string ended with '\x00'.
     */
    @Override
    public byte[] externalOfflinePinData(String encryptPinData, String panBlock) {
        byte[] pinBlock = ConvertHelper.getConvert().strToBcdPaddingLeft(encryptPinData);
        //plaintext PIN
        byte[] plaintextPIN = PedHelper.calcDes(pinBlock);
        //convert pDataIn to BCD format(64个byte)
        byte[] bcdDataIn = ConvertHelper.getConvert().strToBcdPaddingLeft(panBlock);
        //xor with pDataIn
        byte[] xorData = new byte[8];
        System.arraycopy(plaintextPIN,0,xorData,0,plaintextPIN.length);
        for (int i = 0;i<8;i++){
            xorData[i] ^= bcdDataIn[i];
        }
        //get PIN
        byte[] ascPin = ConvertHelper.getConvert().bcdToStr(xorData).getBytes();
        int pinLen = xorData[0];
        //set to pinData,ended with '\x00'"
        byte[] pinData = new byte[pinLen + 1];
        System.arraycopy(ascPin,2,pinData,0,pinLen);
        pinData[pinLen] = 0x00;
        return pinData;
    }

    /**
     * Gets encrypted PinData
     * @param panBlock panBlock
     * @param supportBypass supportBypass
     * @param landscape landscape
     * @return encrypted PinData
     * @throws PinException PinException
     */
    @Override
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
    @Override
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
