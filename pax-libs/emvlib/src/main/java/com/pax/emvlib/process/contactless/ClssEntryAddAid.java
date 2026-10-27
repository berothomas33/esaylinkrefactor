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
import com.pax.emvbase.param.clss.AmexAid;
import com.pax.emvbase.param.clss.DpasAid;
import com.pax.emvbase.param.clss.EFTAid;
import com.pax.emvbase.param.clss.JcbAid;
import com.pax.emvbase.param.clss.MirAid;
import com.pax.emvbase.param.clss.PayPassAid;
import com.pax.emvbase.param.clss.PayWaveAid;
import com.pax.emvbase.param.clss.PbocAid;
import com.pax.emvbase.param.clss.PureAid;
import com.pax.emvbase.param.clss.RuPayAid;
import com.pax.emvlib.base.utils.EmvParamConvert;
import com.pax.jemv.clcommon.Clss_PreProcInfo;
import com.pax.jemv.clcommon.KernType;
import com.pax.jemv.entrypoint.api.ClssEntryApi;

class ClssEntryAddAid {
    private EmvProcessParam emvProcessParam;

    ClssEntryAddAid(EmvProcessParam emvProcessParam){
        this.emvProcessParam = emvProcessParam;
    }

    /**
     * add aid and set entry param, before deteted card, need add all aids
     */
    void addApp(){
        addAmexAid();
        addPayPassAid();
        addPayWaveAid();
        addDpasAid();
        addEFTAid();
        addJcbAid();
        addMirAid();
        addPbocAid();
        addPureAid();
        addRuPayAid();
    }

    private void addPayPassAid(){
        if(emvProcessParam.getPayPassParam() == null
                || emvProcessParam.getPayPassParam().getAidList() == null){
            return;
        }

        for (PayPassAid i : emvProcessParam.getPayPassParam().getAidList()){
            ClssEntryApi.Clss_AddAidList_Entry(i.getAid(),(byte) i.getAid().length,
                    i.getSelFlag(),(byte) KernType.KERNTYPE_DEF);
            Clss_PreProcInfo clss_preProcInfo = EmvParamConvert.PayPassPreProcInfo(i);
            ClssEntryApi.Clss_SetPreProcInfo_Entry(clss_preProcInfo);
        }
    }

    private void addPayWaveAid(){
        if(emvProcessParam.getPayWaveParam() == null
                || emvProcessParam.getPayWaveParam().getAidList() == null
                || emvProcessParam.getEmvTransParam() == null){
            return;
        }

        for (PayWaveAid i : emvProcessParam.getPayWaveParam().getAidList()){
            ClssEntryApi.Clss_AddAidList_Entry(i.getAid(),(byte) i.getAid().length,
                    i.getSelFlag(),(byte) KernType.KERNTYPE_DEF);
            Clss_PreProcInfo clss_preProcInfo = EmvParamConvert.PayWavePreProcInfo(i, emvProcessParam.getEmvTransParam().getTransType());
            ClssEntryApi.Clss_SetPreProcInfo_Entry(clss_preProcInfo);
        }
    }

    private void addAmexAid() {
        if (emvProcessParam.getAmexParam() == null
                || emvProcessParam.getAmexParam().getAidList() == null) {
            return;
        }

        for (AmexAid aid : emvProcessParam.getAmexParam().getAidList()) {
            ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte) aid.getAid().length,
                    aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
            Clss_PreProcInfo clssPreProcInfo = EmvParamConvert.expressPayPreProcInfo(aid);
            ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo);
        }
    }

    private void addDpasAid() {
        if (emvProcessParam.getDpasParam() == null
                || emvProcessParam.getDpasParam().getAidList() == null) {
            return;
        }

        for (DpasAid aid : emvProcessParam.getDpasParam().getAidList()) {
            ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte) aid.getAid().length,
                    aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
            Clss_PreProcInfo clssPreProcInfo = EmvParamConvert.dpasPreProcInfo(aid);
            ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo);
        }
    }

    private void addEFTAid() {
        if (emvProcessParam.getEftParam() == null
                || emvProcessParam.getEftParam().getAidList() == null) {
            return;
        }

        for (EFTAid aid : emvProcessParam.getEftParam().getAidList()) {
            ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte) aid.getAid().length,
                    aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
            Clss_PreProcInfo clssPreProcInfo = EmvParamConvert.eftPreProcInfo(aid);
            ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo);
        }
    }

    private void addJcbAid() {
        if (emvProcessParam.getJcbParam() == null
                || emvProcessParam.getJcbParam().getAidList() == null) {
            return;
        }

        for (JcbAid aid : emvProcessParam.getJcbParam().getAidList()) {
            ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte)aid.getAid().length,
                    aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
            Clss_PreProcInfo clssPreProcInfo = EmvParamConvert.jcbPreProcInfo(aid);
            ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo);
        }
    }

    private void addMirAid() {
        if (emvProcessParam.getMirParam() == null
                || emvProcessParam.getMirParam().getAidList() == null) {
            return;
        }

        for (MirAid aid : emvProcessParam.getMirParam().getAidList()) {
            ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte)aid.getAid().length,
                    aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
            Clss_PreProcInfo clssPreProcInfo = EmvParamConvert.mirPreProcInfo(aid);
            ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo);
        }
    }

    private void addPbocAid() {
        if (emvProcessParam.getPbocParam() == null
                || emvProcessParam.getPbocParam().getAidList() == null) {
            return;
        }

        for (PbocAid aid : emvProcessParam.getPbocParam().getAidList()) {
            ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte) aid.getAid().length, aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
            Clss_PreProcInfo clssPreProcInfo = EmvParamConvert.pbocPreProcInfo(aid);
            ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo);
        }
    }

    private void addPureAid() {
        if (emvProcessParam.getPureParam() == null
                || emvProcessParam.getPureParam().getAidList() == null) {
            return;
        }

        for (PureAid aid : emvProcessParam.getPureParam().getAidList()) {
            ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte)aid.getAid().length,
                    aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
            Clss_PreProcInfo clssPreProcInfo = EmvParamConvert.purePreProcInfo(aid);
            ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo);
        }
    }

    private void addRuPayAid() {
        if (emvProcessParam.getRuPayParam() == null
                || emvProcessParam.getRuPayParam().getAidList() == null) {
            return;
        }

        for (RuPayAid aid : emvProcessParam.getRuPayParam().getAidList()) {
            ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte)aid.getAid().length,
                    aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
            Clss_PreProcInfo clssPreProcInfo = EmvParamConvert.ruPayPreProcInfo(aid);
            ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo);
        }
    }
}
