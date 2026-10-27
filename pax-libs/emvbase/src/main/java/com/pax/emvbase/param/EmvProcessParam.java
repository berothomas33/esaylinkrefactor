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
 * 20200525  	         JackHuang               Create
 * ===========================================================================================
 */

package com.pax.emvbase.param;

import com.pax.emvbase.param.clss.AmexParam;
import com.pax.emvbase.param.clss.DpasParam;
import com.pax.emvbase.param.clss.EFTParam;
import com.pax.emvbase.param.clss.JcbParam;
import com.pax.emvbase.param.clss.MirParam;
import com.pax.emvbase.param.clss.PayPassParam;
import com.pax.emvbase.param.clss.PayWaveParam;
import com.pax.emvbase.param.clss.PbocParam;
import com.pax.emvbase.param.clss.PureParam;
import com.pax.emvbase.param.clss.RuPayParam;
import com.pax.emvbase.param.common.CapkParam;
import com.pax.emvbase.param.common.Config;
import com.pax.emvbase.param.contact.EmvAid;
import java.util.List;

public class EmvProcessParam {

    private List<EmvAid> emvAidList;

    private EmvTransParam emvTransParam;
    private PayWaveParam payWaveParam;
    private CapkParam capkParam;
    private Config termConfig;
    private EFTParam eftParam;
    private MirParam mirParam;
    private PayPassParam payPassParam;
    private DpasParam dpasParam;
    private JcbParam jcbParam;
    private PbocParam pbocParam;
    private PureParam pureParam;
    private RuPayParam ruPayParam;
    private AmexParam amexParam;

    private EmvProcessParam(Builder builder) {
        this.emvAidList = builder.emvAidList;

        this.emvTransParam = builder.emvTransParam;
        this.capkParam = builder.capkParam;
        this.payWaveParam = builder.payWaveParam;
        this.termConfig = builder.termConfig;
        this.eftParam = builder.eftParam;
        this.mirParam = builder.mirParam;
        this.payPassParam = builder.payPassParam;
        this.dpasParam = builder.dpasParam;
        this.jcbParam = builder.jcbParam;
        this.pbocParam = builder.pbocParam;
        this.pureParam = builder.pureParam;
        this.ruPayParam = builder.ruPayParam;
        this.amexParam = builder.amexParam;
    }

    public EmvTransParam getEmvTransParam() {
        return emvTransParam;
    }

    public CapkParam getCapkParam() {
        return capkParam;
    }

    public List<EmvAid> getEmvAidList() {
        return emvAidList;
    }

    public PayWaveParam getPayWaveParam() {
        return payWaveParam;
    }

    public Config getTermConfig() {
        return termConfig;
    }

    public EFTParam getEftParam() {
        return eftParam;
    }

    public MirParam getMirParam() {
        return mirParam;
    }

    public PayPassParam getPayPassParam() {
        return payPassParam;
    }

    public DpasParam getDpasParam() {
        return dpasParam;
    }

    public JcbParam getJcbParam() {
        return jcbParam;
    }

    public PbocParam getPbocParam() {
        return pbocParam;
    }

    public PureParam getPureParam() {
        return pureParam;
    }

    public RuPayParam getRuPayParam() {
        return ruPayParam;
    }

    public AmexParam getAmexParam() {
        return amexParam;
    }

    public static final class Builder {
        private EmvTransParam emvTransParam;
        private CapkParam capkParam;
        private Config termConfig;

        private List<EmvAid> emvAidList;

        private PayWaveParam payWaveParam;
        private EFTParam eftParam;
        private MirParam mirParam;
        private PayPassParam payPassParam;
        private DpasParam dpasParam;
        private JcbParam jcbParam;
        private PbocParam pbocParam;
        private PureParam pureParam;
        private RuPayParam ruPayParam;
        private AmexParam amexParam;

        public Builder() {
        }

        public Builder(EmvTransParam transParam, Config termConfigParam, CapkParam capkParam) {
            this.emvTransParam = transParam;
            this.termConfig = termConfigParam;
            this.capkParam = capkParam;
        }

        public Builder setEmvTransParam(EmvTransParam emvTransParam) {
            this.emvTransParam = emvTransParam;
            return this;
        }

        public Builder setCapkParam(CapkParam capkParam) {
            this.capkParam = capkParam;
            return this;
        }

        public Builder setTermConfig(Config termConfig) {
            this.termConfig = termConfig;
            return this;
        }

        public Builder setEmvAidList(List<EmvAid> emvAidList) {
            this.emvAidList = emvAidList;
            return this;
        }

        public Builder setPayWaveParam(PayWaveParam payWaveParam) {
            this.payWaveParam = payWaveParam;
            return this;
        }

        public Builder setMirParam(MirParam mirParam) {
            this.mirParam = mirParam;
            return this;
        }

        public Builder setEFTParam(EFTParam eftParam) {
            this.eftParam = eftParam;
            return this;
        }

        public Builder setPassParam(PayPassParam passParam) {
            this.payPassParam = passParam;
            return this;
        }

        public Builder setDpasParam(DpasParam dpasParam) {
            this.dpasParam = dpasParam;
            return this;
        }

        public Builder setJcbParam(JcbParam jcbParam) {
            this.jcbParam = jcbParam;
            return this;
        }

        public Builder setPbocParam(PbocParam pbocParam) {
            this.pbocParam = pbocParam;
            return this;
        }

        public Builder setPureParam(PureParam pureParam) {
            this.pureParam = pureParam;
            return this;
        }

        public Builder setRuPayParam(RuPayParam ruPayParam) {
            this.ruPayParam = ruPayParam;
            return this;
        }

        public Builder setAmexParam(AmexParam amexParam) {
            this.amexParam = amexParam;
            return this;
        }

        public EmvProcessParam create() {
            return new EmvProcessParam(this);
        }

    }


}
