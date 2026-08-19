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

    private volatile BaseContactProcess contactProcess;

    private EmvProcess() {
    }

    private static class Holder {
        private static final EmvProcess INSTANCE = new EmvProcess();
    }

    public static EmvProcess getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Resolves and caches the routed contact process on first real use, not at singleton
     * construction. {@link #getInstance()} can fire (via the {@link Holder} lazy-init) before
     * WMRouter has finished registering {@code @RouterService}s — caching a null lookup from
     * that moment in a {@code final} field would strand every later call on the EMV_DENIAL
     * fallback for the rest of the process's life. Re-attempting here instead means a call that
     * arrives after the router is actually ready still gets a working service.
     */
    private BaseContactProcess getContactProcess() {
        BaseContactProcess process = contactProcess;
        if (process == null) {
            synchronized (this) {
                process = contactProcess;
                if (process == null) {
                    process = Router.getService(BaseContactProcess.class, EmvKernelConst.EMV);
                    if (process == null) {
                        LogUtils.e(TAG, "Cannot get contact process!!!");
                    }
                    contactProcess = process;
                }
            }
        }
        return process;
    }

    public void registerEmvProcessListener(IContactCallback emvTransProcessListener) {
        BaseContactProcess process = getContactProcess();
        if (process != null) {
            process.registerEmvProcessListener(emvTransProcessListener);
        }
    }

    @Override
    public int preTransProcess(EmvProcessParam emvProcessParam) {
        BaseContactProcess process = getContactProcess();
        if (process != null) {
            return process.preTransProcess(emvProcessParam);
        }
        return RetCode.EMV_DENIAL;
    }

    public TransResult selectApplication() {
        BaseContactProcess process = getContactProcess();
        if (process != null) {
            return process.selectApplication();
        }
        return new TransResult(RetCode.EMV_DENIAL, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    public TransResult readApplicationData() {
        BaseContactProcess process = getContactProcess();
        if (process != null) {
            return process.readApplicationData();
        }
        return new TransResult(RetCode.EMV_DENIAL, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    public TransResult cardAuthentication() {
        BaseContactProcess process = getContactProcess();
        if (process != null) {
            return process.cardAuthentication();
        }
        return new TransResult(RetCode.EMV_DENIAL, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    @Override
    public TransResult startTransProcess() {
        BaseContactProcess process = getContactProcess();
        if (process != null) {
            return process.startTransProcess();
        }
        return new TransResult(RetCode.EMV_DENIAL, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    @Override
    public TransResult completeTransProcess(IssuerRspData issuerRspData) {
        BaseContactProcess process = getContactProcess();
        if (process != null) {
            return process.completeTransProcess(issuerRspData);
        }
        return new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_ONLINE_CARD_DENIED, CvmResultEnum.CVM_NO_CVM);
    }

    @Override
    public byte[] getTlv(int tag) {
        BaseContactProcess process = getContactProcess();
        if (process != null) {
            return process.getTlv(tag);
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
        BaseContactProcess process = getContactProcess();
        if (process != null) {
            process.setTlv(tag, value);
        }
    }
}
