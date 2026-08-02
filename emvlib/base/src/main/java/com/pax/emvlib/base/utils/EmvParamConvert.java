/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2021-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                           Author                      Action
 * 2022/04/15                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvlib.base.utils;

import com.pax.emvbase.param.clss.AmexAid;
import com.pax.emvbase.param.clss.DpasAid;
import com.pax.emvbase.param.clss.EFTAid;
import com.pax.emvbase.param.clss.JcbAid;
import com.pax.emvbase.param.clss.MirAid;
import com.pax.emvbase.param.clss.PayPassAid;
import com.pax.emvbase.param.clss.PayWaveAid;
import com.pax.emvbase.param.clss.PayWaveInterFloorLimit;
import com.pax.emvbase.param.clss.PbocAid;
import com.pax.emvbase.param.clss.PureAid;
import com.pax.emvbase.param.clss.RuPayAid;
import com.pax.emvbase.param.common.Capk;
import com.pax.emvbase.param.contact.EmvAid;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.jemv.clcommon.Clss_PreProcInfo;
import com.pax.jemv.clcommon.CvmType;
import com.pax.jemv.clcommon.EMV_APPLIST;
import com.pax.jemv.clcommon.EMV_CAPK;
import com.pax.jemv.clcommon.KernType;
import java.util.List;

/**
 * EMV Param Convert Utils
 */
public class EmvParamConvert {
    protected static final String TAG = "EmvParamConvert";

    private EmvParamConvert() {
        // do nothing
    }

    public static EMV_APPLIST toEMVApp(EmvAid aidParam) {
        EMV_APPLIST appList = new EMV_APPLIST();
        appList.appName = aidParam.getLocalAIDName().getBytes();
        appList.aid = aidParam.getApplicationID();
        appList.aidLen = (byte) aidParam.getApplicationID().length;
        appList.selFlag = aidParam.getPartialAIDSelection();
        appList.priority = aidParam.getPriority();
        appList.floorLimit = aidParam.getFloorLimit();
        appList.floorLimitCheck = (byte) (aidParam.getFloorLimit() > 0 ? 1 : 0);
        appList.threshold = aidParam.getThreshold();
        appList.targetPer = aidParam.getTargetPercentage();
        appList.maxTargetPer = aidParam.getMaxTargetPercentage();
        appList.randTransSel = aidParam.getRandTransSel();
        appList.velocityCheck = aidParam.getVelocityCheck();
        appList.tacDenial = aidParam.getTacDenial();
        appList.tacOnline = aidParam.getTacOnline();
        appList.tacDefault = aidParam.getTacDefault();
        appList.acquierId = aidParam.getAcquirerIdBytes();
        appList.dDOL = aidParam.getTerminalDefaultDDOL();
        appList.tDOL = aidParam.getTerminalDefaultTDOL();
        appList.version = aidParam.getTerminalAIDVersion();
        appList.riskManData = aidParam.getTerminalRiskManagementData();
        return appList;
    }

    public static EMV_CAPK toEMVCapk(Capk capk) {
        EMV_CAPK emvCapk = new EMV_CAPK();
        emvCapk.rID = capk.getRid();
        emvCapk.keyID = capk.getKeyId();
        emvCapk.hashInd = capk.getHashArithmeticIndex();
        emvCapk.arithInd = capk.getRsaArithmeticIndex();
        emvCapk.modul = capk.getModule();
        emvCapk.modulLen = (short) capk.getModuleLength();
        emvCapk.exponent = capk.getExponent();
        emvCapk.exponentLen =  capk.getExponentLength();
        emvCapk.expDate = capk.getExpireDate();
        emvCapk.checkSum = capk.getCheckSum();
        return emvCapk;
    }

    public static Clss_PreProcInfo PayPassPreProcInfo(PayPassAid aid){
        Clss_PreProcInfo clssPreProcInfo = new Clss_PreProcInfo();
        clssPreProcInfo.aucAID = aid.getAid();
        clssPreProcInfo.ucAidLen = (byte)aid.getAid().length;
        clssPreProcInfo.ucKernType = KernType.KERNTYPE_MC;
        clssPreProcInfo.ucRdClssFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ucRdClssTxnLmtFlg = aid.getTransLimitFlag();
        clssPreProcInfo.ucRdCVMLmtFlg = aid.getCvmLimitFlag();
        clssPreProcInfo.ucTermFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulRdClssFLmt = aid.getFloorLimit();
        clssPreProcInfo.ulRdCVMLmt = aid.getCvmLimit();
        clssPreProcInfo.ulTermFLmt = aid.getFloorLimit();
        clssPreProcInfo.ulRdClssTxnLmt = aid.getTransLimit();
        return clssPreProcInfo;
    }

    public static Clss_PreProcInfo PayWavePreProcInfo(PayWaveAid aid, byte transType){
        Clss_PreProcInfo clssPreProcInfo = new Clss_PreProcInfo();
        if (aid.getPayWaveInterFloorLimitList() != null) {
            int i = getPayWaveInterFloorLimitIndexByTransType(transType, aid.getPayWaveInterFloorLimitList());
            clssPreProcInfo.aucAID = aid.getAid();
            clssPreProcInfo.ucAidLen = (byte)aid.getAid().length;
            clssPreProcInfo.ucKernType = KernType.KERNTYPE_VIS;
            clssPreProcInfo.ucRdClssFLmtFlg = aid.getPayWaveInterFloorLimitList().get(i).getContactlessFloorLimitSupported();// when in entry, befor
            clssPreProcInfo.ucRdClssTxnLmtFlg = aid.getPayWaveInterFloorLimitList().get(i).getContactlessTransactionLimitSupported();
            clssPreProcInfo.ucRdCVMLmtFlg = aid.getPayWaveInterFloorLimitList().get(i).getCvmLimitSupported();
            clssPreProcInfo.ucTermFLmtFlg = aid.getPayWaveInterFloorLimitList().get(i).getContactlessFloorLimitSupported();
            clssPreProcInfo.ulRdClssFLmt = aid.getPayWaveInterFloorLimitList().get(i).getContactlessFloorLimit();
            clssPreProcInfo.ulRdCVMLmt = aid.getPayWaveInterFloorLimitList().get(i).getContactlessCvmLimit();
            clssPreProcInfo.ulTermFLmt = aid.getPayWaveInterFloorLimitList().get(i).getContactlessFloorLimit();
            clssPreProcInfo.ulRdClssTxnLmt = aid.getPayWaveInterFloorLimitList().get(i).getContactlessTransactionLimit();
            clssPreProcInfo.aucReaderTTQ = aid.getTTQ();
            clssPreProcInfo.ucCrypto17Flg = aid.getCrypto17Flag();
            clssPreProcInfo.ucStatusCheckFlg = aid.getStatusCheckFlag();
            clssPreProcInfo.ucZeroAmtNoAllowed = aid.getZeroAmountNoAllowed();
        }

        return clssPreProcInfo;
    }

    public static Clss_PreProcInfo expressPayPreProcInfo(AmexAid aid) {
        Clss_PreProcInfo clssPreProcInfo = new Clss_PreProcInfo();
        clssPreProcInfo.aucAID = aid.getAid();
        clssPreProcInfo.ucAidLen = (byte) aid.getAid().length;
        clssPreProcInfo.ucKernType = KernType.KERNTYPE_AE;
        clssPreProcInfo.ucTermFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulTermFLmt = aid.getFloorLimit();
        clssPreProcInfo.ucRdClssFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulRdClssFLmt = aid.getFloorLimit();
        clssPreProcInfo.ulRdClssTxnLmt = aid.getTransLimit();
        clssPreProcInfo.ulRdCVMLmt = aid.getCvmLimit();
        return clssPreProcInfo;
    }

    public static Clss_PreProcInfo pbocPreProcInfo(PbocAid aid) {
        Clss_PreProcInfo clssPreProcInfo = new Clss_PreProcInfo();
        clssPreProcInfo.aucAID = aid.getAid();
        clssPreProcInfo.ucAidLen = (byte) aid.getAid().length;
        clssPreProcInfo.ucKernType = KernType.KERNTYPE_PBOC;
        clssPreProcInfo.ucTermFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulTermFLmt = aid.getFloorLimit();
        clssPreProcInfo.ucRdClssTxnLmtFlg = aid.getTransLimitFlag();
        clssPreProcInfo.ucRdClssFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulRdClssFLmt = aid.getFloorLimit();
        clssPreProcInfo.ulRdClssTxnLmt = aid.getTransLimit();
        clssPreProcInfo.ulRdCVMLmt = aid.getCvmLimit();
        clssPreProcInfo.ucRdCVMLmtFlg = aid.getCvmLimitFlag();
        clssPreProcInfo.aucReaderTTQ = aid.getTTQ();
        return clssPreProcInfo;
    }

    public static Clss_PreProcInfo dpasPreProcInfo(DpasAid aid) {
        Clss_PreProcInfo clssPreProcInfo = new Clss_PreProcInfo();
        clssPreProcInfo.aucAID = aid.getAid();
        clssPreProcInfo.ucAidLen = (byte) aid.getAid().length;
        clssPreProcInfo.ucKernType = KernType.KERNTYPE_ZIP;
        clssPreProcInfo.ucTermFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulTermFLmt = aid.getFloorLimit();
        clssPreProcInfo.ucRdClssTxnLmtFlg = aid.getTransLimitFlag();
        clssPreProcInfo.ucRdClssFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulRdClssFLmt = aid.getFloorLimit();
        clssPreProcInfo.ulRdClssTxnLmt = aid.getTransLimit();
        clssPreProcInfo.ulRdCVMLmt = aid.getCvmLimit();
        clssPreProcInfo.ucRdCVMLmtFlg = aid.getCvmLimitFlag();
        clssPreProcInfo.aucReaderTTQ = aid.getTtq();    // TTQ must be set
        return clssPreProcInfo;
    }

    public static Clss_PreProcInfo eftPreProcInfo(EFTAid aid) {
        Clss_PreProcInfo clssPreProcInfo = new Clss_PreProcInfo();
        clssPreProcInfo.aucAID = aid.getAid();
        clssPreProcInfo.ucAidLen = (byte) aid.getAid().length;
        clssPreProcInfo.ucKernType = KernType.KERNTYPE_EFT;
        clssPreProcInfo.ucTermFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulTermFLmt = aid.getFloorLimit();
        clssPreProcInfo.ucRdClssTxnLmtFlg = aid.getTransLimitFlag();
        clssPreProcInfo.ucRdClssFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulRdClssFLmt = aid.getFloorLimit();
        clssPreProcInfo.ulRdClssTxnLmt = aid.getTransLimit();
        clssPreProcInfo.ulRdCVMLmt = aid.getCvmLimit();
        clssPreProcInfo.ucRdCVMLmtFlg = aid.getCvmLimitFlag();
        return clssPreProcInfo;
    }

    public static Clss_PreProcInfo jcbPreProcInfo(JcbAid aid) {
        Clss_PreProcInfo clssPreProcInfo = new Clss_PreProcInfo();
        clssPreProcInfo.aucAID = aid.getAid();
        clssPreProcInfo.ucAidLen = (byte) aid.getAid().length;
        clssPreProcInfo.ucKernType = KernType.KERNTYPE_JCB;
        clssPreProcInfo.ucTermFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulTermFLmt = aid.getFloorLimit();
        clssPreProcInfo.ucRdClssTxnLmtFlg = aid.getTransLimitFlag();
        clssPreProcInfo.ucRdClssFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulRdClssFLmt = aid.getFloorLimit();
        clssPreProcInfo.ulRdClssTxnLmt = aid.getTransLimit();
        clssPreProcInfo.ulRdCVMLmt = aid.getCvmLimit();
        clssPreProcInfo.ucRdCVMLmtFlg = aid.getCvmLimitFlag();
        return clssPreProcInfo;
    }

    public static Clss_PreProcInfo mirPreProcInfo(MirAid aid) {
        Clss_PreProcInfo clssPreProcInfo = new Clss_PreProcInfo();
        clssPreProcInfo.aucAID = aid.getAid();
        clssPreProcInfo.ucAidLen = (byte) aid.getAid().length;
        clssPreProcInfo.ucKernType = KernType.KERNTYPE_MIR;
        clssPreProcInfo.ucTermFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulTermFLmt = aid.getFloorLimit();
        clssPreProcInfo.ucRdClssTxnLmtFlg = aid.getTransLimitFlag();
        clssPreProcInfo.ucRdClssFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulRdClssFLmt = aid.getFloorLimit();
        clssPreProcInfo.ulRdClssTxnLmt = aid.getTransLimit();
        clssPreProcInfo.ulRdCVMLmt = aid.getCvmLimit();
        clssPreProcInfo.ucRdCVMLmtFlg = aid.getCvmLimitFlag();
        return clssPreProcInfo;
    }

    public static Clss_PreProcInfo purePreProcInfo(PureAid aid) {
        Clss_PreProcInfo clssPreProcInfo = new Clss_PreProcInfo();
        clssPreProcInfo.aucAID = aid.getAid();
        clssPreProcInfo.ucAidLen = (byte) aid.getAid().length;
        clssPreProcInfo.ucKernType = KernType.KERNTYPE_PURE;
        clssPreProcInfo.ucTermFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulTermFLmt = aid.getFloorLimit();
        clssPreProcInfo.ucRdClssTxnLmtFlg = aid.getTransLimitFlag();
        clssPreProcInfo.ucRdClssFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulRdClssFLmt = aid.getFloorLimit();
        clssPreProcInfo.ulRdClssTxnLmt = aid.getTransLimit();
        clssPreProcInfo.ulRdCVMLmt = aid.getCvmLimit();
        clssPreProcInfo.ucRdCVMLmtFlg = aid.getCvmLimitFlag();
        return clssPreProcInfo;
    }

    public static Clss_PreProcInfo ruPayPreProcInfo(RuPayAid aid) {
        Clss_PreProcInfo clssPreProcInfo = new Clss_PreProcInfo();
        clssPreProcInfo.aucAID = aid.getAid();
        clssPreProcInfo.ucAidLen = (byte) aid.getAid().length;
        clssPreProcInfo.ucKernType = KernType.KERNTYPE_RUPAY;
        clssPreProcInfo.ucTermFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulTermFLmt = aid.getFloorLimit();
        clssPreProcInfo.ucRdClssTxnLmtFlg = aid.getTransLimitFlag();
        clssPreProcInfo.ucRdClssFLmtFlg = aid.getFloorLimitFlag();
        clssPreProcInfo.ulRdClssFLmt = aid.getFloorLimit();
        clssPreProcInfo.ulRdClssTxnLmt = aid.getTransLimit();
        clssPreProcInfo.ulRdCVMLmt = aid.getCvmLimit();
        clssPreProcInfo.ucRdCVMLmtFlg = aid.getCvmLimitFlag();
        clssPreProcInfo.ucCrypto17Flg = aid.getCrypto17Flag();
        clssPreProcInfo.ucZeroAmtNoAllowed = aid.getZeroAmountNoAllowed();
        clssPreProcInfo.ucStatusCheckFlg = aid.getStatusCheckFlag();
        clssPreProcInfo.aucReaderTTQ = aid.getTTQ();
        clssPreProcInfo.aucRFU = new byte[2];
        return clssPreProcInfo;
    }

    public static CvmResultEnum convertCVM(int result) {
        switch (result) {
            case CvmType.RD_CVM_NO:
                return CvmResultEnum.CVM_NO_CVM;
            case CvmType.RD_CVM_ONLINE_PIN:
            case CvmType.RD_CVM_REQ_ONLINE_PIN:
                return CvmResultEnum.CVM_ONLINE_PIN;
            case CvmType.RD_CVM_SIG:
            case CvmType.RD_CVM_REQ_SIG:
                return CvmResultEnum.CVM_SIG;
            case CvmType.RD_CVM_CONSUMER_DEVICE:
                return CvmResultEnum.CVM_CONSUMER_DEVICE;
            case CvmType.RD_CVM_OFFLINE_PIN:
                return CvmResultEnum.CVM_OFFLINE_PIN;
            default:
                return null;
        }
    }

    public static int getPayWaveInterFloorLimitIndexByTransType(byte transType, List<PayWaveInterFloorLimit> list){
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getTransactionType() == transType) {
                return i;
            }
        }
        return 0;
    }
}
