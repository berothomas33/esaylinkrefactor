/*
 *  ===========================================================================================
 *  = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *     This software is supplied under the terms of a license agreement or nondisclosure
 *     agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *     disclosed except in accordance with the terms in that agreement.
 *          Copyright (C) 2020 -? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 *  Description: // Detail description about the function of this module,
 *               // interfaces with the other modules, and dependencies.
 *  Revision History:
 *  Date	               Author	                   Action
 *  2020/05/26 	         Qinny Zhou           	      Create
 *  ===========================================================================================
 */

package com.pax.emvlib.process.contact;

import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.process.EmvBase;
import com.pax.emvbase.process.contact.IContactCallback;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvlib.base.consts.EmvKernelConst;
import com.pax.emvlib.base.contact.BaseContactProcess;
import com.pax.jemv.clcommon.RetCode;
import com.sankuai.waimai.router.Router;

public class EmvProcess extends EmvBase {
    private static final String TAG = "EmvProcess";

    private final BaseContactProcess contactProcess;

    private EmvProcess() {
        contactProcess = Router.getService(BaseContactProcess.class, EmvKernelConst.EMV);
        if (contactProcess == null) {
            LogUtils.e(TAG, "Cannot get contact process!!!");
        }
    }

    private static class Holder {
        private static final EmvProcess INSTANCE = new EmvProcess();
    }

    public static EmvProcess getInstance() {
        return Holder.INSTANCE;
    }

    public void registerEmvProcessListener(IContactCallback emvTransProcessListener) {
        if (contactProcess != null) {
            contactProcess.registerEmvProcessListener(emvTransProcessListener);
        }
    }

    @Override
    public int preTransProcess(EmvProcessParam emvProcessParam) {
        if (contactProcess != null) {
            return contactProcess.preTransProcess(emvProcessParam);
        }
        return RetCode.EMV_DENIAL;
    }

    public TransResult selectApplication() {
        if (contactProcess != null) {
            return contactProcess.selectApplication();
        }
        return new TransResult(RetCode.EMV_DENIAL, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    public TransResult readApplicationData() {
        if (contactProcess != null) {
            return contactProcess.readApplicationData();
        }
        return new TransResult(RetCode.EMV_DENIAL, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    public TransResult cardAuthentication() {
        if (contactProcess != null) {
            return contactProcess.cardAuthentication();
        }
        return new TransResult(RetCode.EMV_DENIAL, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    @Override
    public TransResult startTransProcess() {
        if (contactProcess != null) {
            return contactProcess.startTransProcess();
        }
        return new TransResult(RetCode.EMV_DENIAL, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    @Override
    public TransResult completeTransProcess(IssuerRspData issuerRspData) {
        if (contactProcess != null) {
            return contactProcess.completeTransProcess(issuerRspData);
        }
        return new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_ONLINE_CARD_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    @Override
    public byte[] getTlv(int tag) {
        if (contactProcess != null) {
            return contactProcess.getTlv(tag);
        }
        return new byte[0];
    }

    /**
     * Sets value on specific tag
     *
     * @param tag   emv tag
     * @param value tag value
     */
    @Override
    public void setTlv(int tag, byte[] value) {
        if (contactProcess != null) {
            contactProcess.setTlv(tag, value);
        }
    }
}
