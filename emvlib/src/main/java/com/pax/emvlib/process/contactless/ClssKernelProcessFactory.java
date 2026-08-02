/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2020-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                  Author	                 Action
 * 20200528  	         JackHuang               Create
 * ===========================================================================================
 */
package com.pax.emvlib.process.contactless;

import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.clss.BaseParam;
import com.pax.emvbase.process.contactless.IContactlessCallback;
import com.pax.emvlib.base.consts.EmvKernelConst;
import com.pax.emvlib.base.contactless.ClssKernelProcess;
import com.pax.jemv.clcommon.Clss_PreProcInterInfo;
import com.pax.jemv.clcommon.Clss_TransParam;
import com.pax.jemv.clcommon.KernType;
import com.sankuai.waimai.router.Router;

class ClssKernelProcessFactory {
    private int kernelType;

    private BaseParam clssParam;

    private EmvProcessParam emvProcessParam;
    private byte[] finalSelectData;
    private int finalSelectDataLen;
    private Clss_TransParam transParam;
    private Clss_PreProcInterInfo preProcInterInfo;
    private IContactlessCallback clssStatusListener;

    public ClssKernelProcessFactory(int kernelType) {
        this.kernelType = kernelType;
    }

    /**
     * when need to add a new clss kernel, create a object here
     * @param kernelType KernelType
     * @return ClssKernelProcess object
     */
    private ClssKernelProcess getKernelProcess(int kernelType){
        switch (kernelType) {
            case KernType.KERNTYPE_VIS:
                clssParam = emvProcessParam.getPayWaveParam();
                return Router.getService(ClssKernelProcess.class, EmvKernelConst.PAYWAVE);
            case KernType.KERNTYPE_MC:
                clssParam = emvProcessParam.getPayPassParam();
                return Router.getService(ClssKernelProcess.class, EmvKernelConst.MC);
            case KernType.KERNTYPE_AE:
                clssParam = emvProcessParam.getAmexParam();
                return Router.getService(ClssKernelProcess.class, EmvKernelConst.AMEX);
            case KernType.KERNTYPE_PBOC:
                clssParam = emvProcessParam.getPbocParam();
                return Router.getService(ClssKernelProcess.class, EmvKernelConst.PBOC);
            case KernType.KERNTYPE_EFT:
                clssParam = emvProcessParam.getEftParam();
                return Router.getService(ClssKernelProcess.class, EmvKernelConst.EFT);
            case KernType.KERNTYPE_JCB:
                clssParam = emvProcessParam.getJcbParam();
                return Router.getService(ClssKernelProcess.class, EmvKernelConst.JCB);
            case KernType.KERNTYPE_MIR:
                clssParam = emvProcessParam.getMirParam();
                return Router.getService(ClssKernelProcess.class, EmvKernelConst.MIR);
            case KernType.KERNTYPE_PURE:
                clssParam = emvProcessParam.getPureParam();
                return Router.getService(ClssKernelProcess.class, EmvKernelConst.PURE);
            case KernType.KERNTYPE_RUPAY:
                clssParam = emvProcessParam.getRuPayParam();
                return Router.getService(ClssKernelProcess.class, EmvKernelConst.RUPAY);
            case KernType.KERNTYPE_ZIP:
                clssParam = emvProcessParam.getDpasParam();
                return Router.getService(ClssKernelProcess.class, EmvKernelConst.DPAS);
            default:
                throw new IllegalArgumentException("Unsupported Kernel " + kernelType);
        }
    }

    public ClssKernelProcessFactory setEmvProcessParam(EmvProcessParam emvProcessParam) {
        this.emvProcessParam = emvProcessParam;
        return this;
    }

    public ClssKernelProcessFactory setClssTransParam(Clss_TransParam transParam) {
        this.transParam = transParam;
        return this;
    }

    public ClssKernelProcessFactory setFinalSelectData(byte[] finalSelectData, int finalSelectDataLen) {
        this.finalSelectData = finalSelectData;
        this.finalSelectDataLen = finalSelectDataLen;
        return this;
    }

    public ClssKernelProcessFactory setPreProcInterInfo(Clss_PreProcInterInfo preProcInterInfo) {
        this.preProcInterInfo = preProcInterInfo;
        return this;
    }

    public ClssKernelProcessFactory setClssStatusListener(IContactlessCallback clssStatusListener) {
        this.clssStatusListener = clssStatusListener;
        return this;
    }

    public ClssKernelProcess build() {
        return getKernelProcess(kernelType)
                .setEmvProcessParam(emvProcessParam)
                .setClssTransParam(transParam)
                .setFinalSelectData(finalSelectData, finalSelectDataLen)
                .setPreProcInterInfo(preProcInterInfo)
                .setClssStatusListener(clssStatusListener)
                .setClssParam(clssParam.loadSelectedAid(finalSelectData)
                                .loadFromConfig(emvProcessParam.getTermConfig())
                                .loadFromEmvTransParam(emvProcessParam.getEmvTransParam()));
    }
}
