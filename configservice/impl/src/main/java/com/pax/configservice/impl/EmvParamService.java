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
 * 20210509 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.configservice.impl;

import androidx.annotation.NonNull;
import com.pax.bizentity.db.helper.AmexAidDbHelper;
import com.pax.bizentity.db.helper.AmexDrlDbHelper;
import com.pax.bizentity.db.helper.CapkRevokeDbHelper;
import com.pax.bizentity.db.helper.DpasAidDbHelper;
import com.pax.bizentity.db.helper.EFTAidDbHelper;
import com.pax.bizentity.db.helper.GreendaoHelper;
import com.pax.bizentity.db.helper.JcbAidDbHelper;
import com.pax.bizentity.db.helper.MirAidDbHelper;
import com.pax.bizentity.db.helper.PBOCAidDbHelper;
import com.pax.bizentity.db.helper.PaypassAidDbHelper;
import com.pax.bizentity.db.helper.PaywaveAidDbHelper;
import com.pax.bizentity.db.helper.PaywaveDrlDbHelper;
import com.pax.bizentity.db.helper.PaywaveFloorLimitDbHelper;
import com.pax.bizentity.db.helper.PureAidDbHelper;
import com.pax.bizentity.db.helper.RupayAidDbHelper;
import com.pax.bizentity.entity.Acquirer;
import com.pax.bizentity.entity.CapkParamBean;
import com.pax.bizentity.entity.CapkRevokeBean;
import com.pax.bizentity.entity.EmvAid;
import com.pax.bizentity.entity.EmvCapk;
import com.pax.bizentity.entity.clss.amex.AmexAidBean;
import com.pax.bizentity.entity.clss.amex.AmexDrlBean;
import com.pax.bizentity.entity.clss.amex.AmexParamBean;
import com.pax.bizentity.entity.clss.dpas.DpasAidBean;
import com.pax.bizentity.entity.clss.dpas.DpasParamBean;
import com.pax.bizentity.entity.clss.eft.EFTAidBean;
import com.pax.bizentity.entity.clss.eft.EFTParamBean;
import com.pax.bizentity.entity.clss.jcb.JcbAidBean;
import com.pax.bizentity.entity.clss.jcb.JcbParamBean;
import com.pax.bizentity.entity.clss.mir.MirAidBean;
import com.pax.bizentity.entity.clss.mir.MirParamBean;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;
import com.pax.bizentity.entity.clss.paypass.PayPassParamBean;
import com.pax.bizentity.entity.clss.paywave.PayWaveInterFloorLimitBean;
import com.pax.bizentity.entity.clss.paywave.PayWaveParamBean;
import com.pax.bizentity.entity.clss.paywave.PaywaveAidBean;
import com.pax.bizentity.entity.clss.paywave.PaywaveDrlBean;
import com.pax.bizentity.entity.clss.pboc.PBOCAidBean;
import com.pax.bizentity.entity.clss.pboc.PBOCParamBean;
import com.pax.bizentity.entity.clss.pure.PureAidBean;
import com.pax.bizentity.entity.clss.pure.PureParamBean;
import com.pax.bizentity.entity.clss.rupay.RupayAidBean;
import com.pax.bizentity.entity.clss.rupay.RupayParamBean;
import com.pax.commonlib.currency.CountryCode;
import com.pax.commonlib.currency.CurrencyConverter;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.export.ConfigKeyConstant;
import com.pax.configservice.export.ConfigServiceConstant;
import com.pax.configservice.export.IAcquirerIssuerService;
import com.pax.configservice.export.IConfigParamService;
import com.pax.configservice.export.IEmvParamService;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.clss.AmexAid;
import com.pax.emvbase.param.clss.AmexParam;
import com.pax.emvbase.param.clss.DpasAid;
import com.pax.emvbase.param.clss.DpasParam;
import com.pax.emvbase.param.clss.EFTAid;
import com.pax.emvbase.param.clss.EFTParam;
import com.pax.emvbase.param.clss.ExPayDRL;
import com.pax.emvbase.param.clss.JcbAid;
import com.pax.emvbase.param.clss.JcbParam;
import com.pax.emvbase.param.clss.MirAid;
import com.pax.emvbase.param.clss.MirParam;
import com.pax.emvbase.param.clss.PayPassAid;
import com.pax.emvbase.param.clss.PayPassParam;
import com.pax.emvbase.param.clss.PayWaveAid;
import com.pax.emvbase.param.clss.PayWaveInterFloorLimit;
import com.pax.emvbase.param.clss.PayWaveParam;
import com.pax.emvbase.param.clss.PayWaveProgramId;
import com.pax.emvbase.param.clss.PbocAid;
import com.pax.emvbase.param.clss.PbocParam;
import com.pax.emvbase.param.clss.PureAid;
import com.pax.emvbase.param.clss.PureParam;
import com.pax.emvbase.param.clss.RuPayAid;
import com.pax.emvbase.param.clss.RuPayParam;
import com.pax.emvbase.param.common.Capk;
import com.pax.emvbase.param.common.CapkParam;
import com.pax.emvbase.param.common.CapkRevoke;
import com.pax.emvbase.param.common.Config;
import com.sankuai.waimai.router.annotation.RouterService;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@RouterService(interfaces = IEmvParamService.class,key = ConfigServiceConstant.CONFIGSERVICE_EMVPARAM,singleton = true)
public class EmvParamService implements IEmvParamService {
    private static final String TAG = "EmvParamService";

    private final EmvProcessParam.Builder cachedBuilder;
    public EmvParamService() {
        cachedBuilder = new EmvProcessParam.Builder();
    }

    /**
     * insert EmvCapk
     *
     * @param emvCapkList EmvCapk list
     * @return insert result
     */
    @Override
    public boolean insertEmvCapk(CapkParamBean emvCapkList) {
        if (emvCapkList == null){
            return false;
        }
        List<EmvCapk> capkList = emvCapkList.getCapkList();
        List<CapkRevokeBean> capkRevokeList = emvCapkList.getCapkRevokeList();
        boolean capkInsert = false;
        if (capkList != null){
            capkInsert = GreendaoHelper.getEmvCapkHelper().insert(capkList);
        }
        if (capkRevokeList != null){
            CapkRevokeDbHelper.getInstance().insert(capkRevokeList);
        }
        cachedBuilder.setCapkParam(capkConvert(emvCapkList));
        return capkInsert;
    }

    @Override
    public void removeEmvCapk() {
        GreendaoHelper.getEmvCapkHelper().deleteAll();
    }

    /**
     * insert terminal config
     *
     * @return insert result
     */
    @Override
    public boolean insertTerminalConfig() {
        cachedBuilder.setTermConfig(getTerminalConfig());
        return true;
    }

    @Override
    public void removeTerminalConfig() {
        cachedBuilder.setTermConfig(null);
    }

    /**
     * insert EmvAid
     *
     * @param emvAidList EmvAid list
     * @return insert result
     */
    @Override
    public boolean insertEmvAid(List<EmvAid> emvAidList) {
        if (emvAidList == null){
            return false;
        }
        boolean insert = GreendaoHelper.getEmvAidHelper().insert(emvAidList);
        if (insert){
            cachedBuilder.setEmvAidList(emvAidConvert(emvAidList));
        }
        return insert;
    }

    @Override
    public void removeEmvAid() {
        GreendaoHelper.getEmvAidHelper().deleteAll();
    }

    /**
     * insert amex param
     *
     * @param amexParam amex param
     * @return insert result
     */
    @Override
    public boolean insertAmexParam(AmexParamBean amexParam) {
        List<AmexAidBean> aid = amexParam.getAid();
        List<AmexDrlBean> programID = amexParam.getProgramID();
        boolean aidInsert = false;
        if (aid != null){
            aidInsert = AmexAidDbHelper.getInstance().insert(aid);
        }
        if (programID != null){
            AmexDrlDbHelper.getInstance().insert(programID);
        }
        cachedBuilder.setAmexParam(aemxConvert(amexParam));
        return aidInsert;
    }

    @Override
    public void removeAmexParam() {
        AmexAidDbHelper.getInstance().deleteAll();
        AmexDrlDbHelper.getInstance().deleteAll();
    }

    /**
     * insert paypass param
     *
     * @param payPassParam paypass param
     * @return insert result
     */
    @Override
    public boolean insertPaypassParam(PayPassParamBean payPassParam) {
        List<PayPassAidBean> aid = payPassParam.getAid();
        boolean aidInsert = false;
        if (aid != null){
            aidInsert = PaypassAidDbHelper.getInstance().insert(aid);
        }
        cachedBuilder.setPassParam(paypassConvert(payPassParam));
        return aidInsert;
    }

    @Override
    public void removePaypassParam() {
        PaypassAidDbHelper.getInstance().deleteAll();
    }

    /**
     * insert payWave ParamBean
     *
     * @param payWaveParamBean payWave ParamBean
     * @return insert result
     */
    @Override
    public boolean insertPaywaveParam(PayWaveParamBean payWaveParamBean) {
        List<PaywaveAidBean> aid = payWaveParamBean.getAid();
        List<PaywaveDrlBean> programID = payWaveParamBean.getProgramID();
        boolean aidInsert = false;
        if (aid != null){
            aidInsert = PaywaveAidDbHelper.getInstance().insert(aid);
            for (PaywaveAidBean item : aid){
                List<PayWaveInterFloorLimitBean> interWareFloorLimit = item.getInterWareFloorLimit();
                if (interWareFloorLimit == null){
                    continue;
                }
                for (PayWaveInterFloorLimitBean interFloorLimitBean: interWareFloorLimit){
                    interFloorLimitBean.setPaywaveAidId(item.getId());
                }
                PaywaveFloorLimitDbHelper.getInstance().insert(interWareFloorLimit);
            }
        }
        if (programID != null){
            PaywaveDrlDbHelper.getInstance().insert(programID);
        }
        cachedBuilder.setPayWaveParam(paywaveConvert(payWaveParamBean));
        return aidInsert;
    }

    @Override
    public void removePaywaveParam() {
        PaywaveAidDbHelper.getInstance().deleteAll();
        PaywaveFloorLimitDbHelper.getInstance().deleteAll();
        PaywaveDrlDbHelper.getInstance().deleteAll();
    }

    /**
     * insert dpas param
     *
     * @param dpasParamBean dpas param
     * @return insert result
     */
    @Override
    public boolean insertDpasParam(DpasParamBean dpasParamBean) {
        List<DpasAidBean> aid = dpasParamBean.getAid();
        boolean aidInsert = false;
        if (aid != null){
            aidInsert = DpasAidDbHelper.getInstance().insert(aid);
        }
        cachedBuilder.setDpasParam(dpasConvert(dpasParamBean));
        return aidInsert;
    }

    @Override
    public void removeDpasParam() {
        DpasAidDbHelper.getInstance().deleteAll();
    }

    /**
     * insert eft param
     *
     * @param eftParamBean eft param
     * @return insert result
     */
    @Override
    public boolean insertEFTParam(EFTParamBean eftParamBean) {
        List<EFTAidBean> aid = eftParamBean.getAid();
        boolean aidInsert = false;
        if (aid != null){
            aidInsert = EFTAidDbHelper.getInstance().insert(aid);
        }
        cachedBuilder.setEFTParam(eftConvert(eftParamBean));
        return aidInsert;
    }

    @Override
    public void removeEFTParam() {
        EFTAidDbHelper.getInstance().deleteAll();
    }

    /**
     * insert jcb param
     *
     * @param jcbParamBean jcb param
     * @return insert result
     */
    @Override
    public boolean insertJcbParam(JcbParamBean jcbParamBean) {
        List<JcbAidBean> aid = jcbParamBean.getAid();
        boolean aidInsert = false;
        if (aid != null){
            aidInsert = JcbAidDbHelper.getInstance().insert(aid);
        }
        cachedBuilder.setJcbParam(jcbConvert(jcbParamBean));
        return aidInsert;
    }

    @Override
    public void removeJcbParam() {
        JcbAidDbHelper.getInstance().deleteAll();
    }

    /**
     * insert mir param
     *
     * @param mirParamBean mir param
     * @return insert result
     */
    @Override
    public boolean insertMirParam(MirParamBean mirParamBean) {
        List<MirAidBean> aid = mirParamBean.getAid();
        boolean aidInsert = false;
        if (aid != null){
            aidInsert = MirAidDbHelper.getInstance().insert(aid);
        }
        cachedBuilder.setMirParam(mirConvert(mirParamBean));
        return aidInsert;
    }

    @Override
    public void removeMirParam() {
        MirAidDbHelper.getInstance().deleteAll();
    }

    /**
     * insert pboc param
     *
     * @param pbocParamBean pboc param
     * @return insert result
     */
    @Override
    public boolean insertPBOCParam(PBOCParamBean pbocParamBean) {
        List<PBOCAidBean> aid = pbocParamBean.getAid();
        boolean aidInsert = false;
        if (aid != null){
            aidInsert = PBOCAidDbHelper.getInstance().insert(aid);
        }
        cachedBuilder.setPbocParam(pbocConvert(pbocParamBean));
        return aidInsert;
    }

    @Override
    public void removePBOCParam() {
        PBOCAidDbHelper.getInstance().deleteAll();
    }

    /**
     * insert pure param
     *
     * @param pureParamBean pure param
     * @return insert result
     */
    @Override
    public boolean insertPureParam(PureParamBean pureParamBean) {
        List<PureAidBean> aid = pureParamBean.getAid();
        boolean aidInsert = false;
        if (aid != null){
            aidInsert = PureAidDbHelper.getInstance().insert(aid);
        }
        cachedBuilder.setPureParam(pureConvert(pureParamBean));
        return aidInsert;
    }

    @Override
    public void removePureParam() {
        PureAidDbHelper.getInstance().deleteAll();
    }

    /**
     * insert rupay param
     *
     * @param rupayParamBean rupay param
     * @return insert result
     */
    @Override
    public boolean insertRupayParam(RupayParamBean rupayParamBean) {
        List<RupayAidBean> aid = rupayParamBean.getAid();
        boolean aidInsert = false;
        if (aid != null){
            aidInsert = RupayAidDbHelper.getInstance().insert(aid);
        }
        cachedBuilder.setRuPayParam(rupayConvert(rupayParamBean));
        return aidInsert;
    }

    @Override
    public void removeRupayParam() {
        RupayAidDbHelper.getInstance().deleteAll();
    }

    private RuPayParam rupayConvert(RupayParamBean rupayParamBean) {
        List<RupayAidBean> aids = rupayParamBean.getAid();
        RuPayParam param = new RuPayParam();
        if (aids != null && aids.size() > 0){
            List<RuPayAid> ruPayAids = new ArrayList<>();
            for (RupayAidBean item: aids){
                RuPayAid ruPayAid = new RuPayAid();
                ruPayAid.setAppName(item.getAppNameBytes());
                ruPayAid.setAid(item.getAidBytes());
                ruPayAid.setSelFlag(item.getSelFlag());
                ruPayAid.setTransLimit(item.getTransLimit());
                ruPayAid.setTransLimitBytes(item.getTransLimitBytes());
                ruPayAid.setTransLimitFlag(item.getTransLimitFlag());
                ruPayAid.setFloorLimit(item.getFloorLimit());
                ruPayAid.setFloorLimitBytes(item.getFloorLimitBytes());
                ruPayAid.setFloorLimitFlag(item.getFloorLimitFlag());
                ruPayAid.setCvmLimit(item.getCvmLimit());
                ruPayAid.setCvmLimitBytes(item.getCvmLimitBytes());
                ruPayAid.setCvmLimitFlag(item.getCvmLimitFlag());
                ruPayAid.setTacDenial(item.getTacDenialBytes());
                ruPayAid.setTacOnline(item.getTacOnlineBytes());
                ruPayAid.setTacDefault(item.getTacDefaultBytes());
                ruPayAid.setAcquirerId(item.getAcquirerIdBytes());
                ruPayAid.setVersion(item.getVersionBytes());
                ruPayAid.setTermType(item.getTerminalTypeByte());
                ruPayAid.setTermCapability(item.getTerminalCapabilityBytes());
                ruPayAid.setAddCapability(item.getTerminalAdditionalCapabilityBytes());
                ruPayAid.setServiceIdBytes(item.getServiceIdBytes());
                ruPayAid.setCrypto17Flag(item.getCrypto17Flag());
                ruPayAid.setZeroAmountNoAllowed(item.getZeroAmountNoAllowed());
                ruPayAid.setStatusCheckFlag(item.getStatusCheckFlag());
                ruPayAid.setTTQ(item.getTtqBytes());
                ruPayAids.add(ruPayAid);
            }
            param.setAidList(ruPayAids);
        }
        return param;
    }

    private PureParam pureConvert(PureParamBean pureParamBean) {
        List<PureAidBean> aids = pureParamBean.getAid();
        PureParam param = new PureParam();
        if (aids != null && aids.size() > 0){
            List<PureAid> pureAids = new ArrayList<>();
            for (PureAidBean item: aids){
                PureAid pureAid = new PureAid();
                pureAid.setAppName(item.getAppNameBytes());
                pureAid.setAid(item.getAidBytes());
                pureAid.setSelFlag(item.getSelFlag());
                pureAid.setTransLimit(item.getTransLimit());
                pureAid.setTransLimitFlag(item.getTransLimitFlag());
                pureAid.setFloorLimit(item.getFloorLimit());
                pureAid.setFloorLimitFlag(item.getFloorLimitFlag());
                pureAid.setCvmLimit(item.getCvmLimit());
                pureAid.setCvmLimitFlag(item.getCvmLimitFlag());
                pureAid.setTacDenial(item.getTacDenialBytes());
                pureAid.setTacOnline(item.getTacOnlineBytes());
                pureAid.setTacDefault(item.getTacDefaultBytes());
                pureAid.setAcquirerId(item.getAcquirerIdBytes());
                pureAid.setDefaultDDOL(item.getdDOLBytes());
                pureAid.setVersion(item.getVersionBytes());
                pureAid.setTermType(item.getTerminalTypeByte());
                pureAid.setTermCapability(item.getTerminalCapabilityBytes());
                pureAid.setAddCapability(item.getTerminalAdditionalCapabilityBytes());
                pureAid.setAddTagObjList(item.getAddTagObjListBytes());
                pureAid.setMandatoryTagObjList(item.getMandatoryTagObjListBytes());
                pureAid.setAuthTransDataTagObjList(item.getAuthTransDataTagObjListBytes());
                pureAid.setClssKernelCapability(item.getClssKernelCapabilityBytes());
                pureAid.setClssPOSImplOption(item.getClssPOSImplOptionBytes());
                pureAid.setAppAuthTransType(item.getAppAuthTransTypeBytes());
                pureAid.setRefundTacDenial(item.getRefundTacDenialBytes());
                pureAid.setTimeout(item.getTimeoutBytes());
                pureAid.setMemorySlotReadTemp(item.getMemorySlotReadTempBytes());
                pureAid.setMemorySlotUpdateTemp(item.getMemorySlotUpdateTempBytes());
                pureAid.setImpOption(item.getImpOptionBytes());
                pureAid.setCheckExceptionFilePan(item.getCheckExceptionFilePanBytes());
                pureAids.add(pureAid);
            }
            param.setAidList(pureAids);
        }
        return param;
    }

    private PbocParam pbocConvert(PBOCParamBean pbocParamBean) {
        List<PBOCAidBean> aids = pbocParamBean.getAid();
        PbocParam param = new PbocParam();
        if (aids != null && aids.size() > 0){
            List<PbocAid> pbocAids = new ArrayList<>();
            for (PBOCAidBean item: aids){
                PbocAid pbocAid = new PbocAid();
                pbocAid.setAppName(item.getAppNameBytes());
                pbocAid.setAid(item.getAidBytes());
                pbocAid.setAidValue(item.getAid());
                pbocAid.setSelFlag(item.getSelFlag());
                pbocAid.setTransLimit(item.getTransLimit());
                pbocAid.setTransLimitFlag(item.getTransLimitFlag());
                pbocAid.setFloorLimit(item.getFloorLimit());
                pbocAid.setFloorLimitFlag(item.getFloorLimitFlag());
                pbocAid.setCvmLimit(item.getCvmLimit());
                pbocAid.setCvmLimitFlag(item.getCvmLimitFlag());
                pbocAid.setAcquirerId(item.getAcquirerIdBytes());
                pbocAid.setVersion(item.getVersionBytes());
                pbocAid.setTermType(item.getTerminalTypeByte());
                pbocAid.setTermCapability(item.getTerminalCapabilityBytes());
                pbocAid.setTermAddCapability(item.getTerminalAdditionalCapabilityBytes());
                pbocAid.setQuicsFlag(item.getQuicsFlag());
                pbocAid.setTornMaxLifeTime(item.getTornMaxLifeTime());
                pbocAid.setTornLogMaxNum((short) item.getTornLogMaxNum());
                pbocAid.setTornSupport(item.getTornSupport());
                pbocAid.setTornRFU(item.getTornRFUBytes());
                pbocAid.setAucRFU(item.getAucRFUBytes());
                pbocAid.setTTQ(item.getTtqBytes());
                pbocAid.setAidType(item.getAidType());
                pbocAid.setQpsLimit(item.getQpsLimit());
                pbocAids.add(pbocAid);
            }
            param.setAidList(pbocAids);
        }
        return param;
    }

    private MirParam mirConvert(MirParamBean mirParamBean) {
        List<MirAidBean> aids = mirParamBean.getAid();
        MirParam param = new MirParam();
        if (aids != null && aids.size() > 0){
            List<MirAid> mirAids = new ArrayList<>();
            for (MirAidBean item: aids){
                MirAid mirAid = new MirAid();
                mirAid.setAppName(item.getAppNameBytes());
                mirAid.setAid(item.getAidBytes());
                mirAid.setSelFlag(item.getSelFlag());

                mirAid.setTransLimitBytes(item.getTransLimitBytes());
                mirAid.setCvmLimitBytes(item.getCvmLimitBytes());
                mirAid.setFloorLimitBytes(item.getFloorLimitBytes());

                mirAid.setTransLimit(item.getTransLimit());
                mirAid.setTransLimitFlag(item.getTransLimitFlag());
                mirAid.setFloorLimit(item.getFloorLimit());
                mirAid.setFloorLimitFlag(item.getFloorLimitFlag());
                mirAid.setCvmLimit(item.getCvmLimit());
                mirAid.setCvmLimitFlag(item.getCvmLimitFlag());
                mirAid.setTacDenial(item.getTacDenialBytes());
                mirAid.setTacOnline(item.getTacOnlineBytes());
                mirAid.setTacDefault(item.getTacDefaultBytes());
                mirAid.setAcquirerId(item.getAcquirerIdBytes());
                mirAid.setVersion(item.getVersionBytes());
                mirAid.setTermType(item.getTerminalTypeByte());
                mirAid.setRefundFloorLimit(item.getRefundFloorLimitBytes());
                mirAid.setRefundTacDenial(item.getRefundTacDenialBytes());
                mirAid.setTermTPMCapability(item.getTermTPMCapabilityBytes());
                mirAid.setTlvDF810CTag(item.getTlvDF810CTagBytes());
                mirAid.setTransRecoveryLimit(item.getTransRecoveryLimitBytes());
                mirAid.setDataExchangeTagList(item.getDataExchangeTagListBytes());
                mirAid.setProtocol2Tag82(item.getProtocol2Tag82Bytes());
                mirAid.setExceptFileFlag(item.getExceptFileFlag());
                mirAids.add(mirAid);
            }
            param.setAidList(mirAids);
        }
        return param;
    }

    private JcbParam jcbConvert(JcbParamBean jcbParamBean) {
        List<JcbAidBean> aids = jcbParamBean.getAid();
        JcbParam param = new JcbParam();
        if (aids != null && aids.size() > 0){
            List<JcbAid> jcbAids = new ArrayList<>();
            for (JcbAidBean item: aids){
                JcbAid jcbAid = new JcbAid();
                jcbAid.setAppName(item.getAppNameBytes());
                jcbAid.setAid(item.getAidBytes());
                jcbAid.setSelFlag(item.getSelFlag());

                jcbAid.setTransLimitBytes(item.getTransLimitBytes());
                jcbAid.setCvmLimitBytes(item.getCvmLimitBytes());
                jcbAid.setFloorLimitBytes(item.getFloorLimitBytes());
                jcbAid.setCvmTransLimitBytes(item.getCvmTransLimitBytes());

                jcbAid.setTransLimit(item.getTransLimit());
                jcbAid.setTransLimitFlag(item.getTransLimitFlag());
                jcbAid.setFloorLimit(item.getFloorLimit());
                jcbAid.setFloorLimitFlag(item.getFloorLimitFlag());
                jcbAid.setCvmLimit(item.getCvmLimit());
                jcbAid.setCvmLimitFlag(item.getCvmLimitFlag());
                jcbAid.setTacDenial(item.getTacDenialBytes());
                jcbAid.setTacOnline(item.getTacOnlineBytes());
                jcbAid.setTacDefault(item.getTacDefaultBytes());
                jcbAid.setAcquirerId(item.getAcquirerIdBytes());
                jcbAid.setVersion(item.getVersionBytes());
                jcbAid.setTermType(item.getTerminalTypeByte());
                jcbAid.setTermInterchange(item.getTermInterchangeBytes());
                jcbAid.setTermCompatFlag(new byte[]{item.getTermCompatFlag()});
                jcbAid.setCombinationOption(item.getCombinationOptionBytes());
                jcbAids.add(jcbAid);
            }
            param.setAidList(jcbAids);
        }
        return param;
    }

    private EFTParam eftConvert(EFTParamBean eftParamBean) {
        List<EFTAidBean> aids = eftParamBean.getAid();
        EFTParam param = new EFTParam();
        if (aids != null && aids.size() > 0){
            List<EFTAid> eftAids = new ArrayList<>();
            for (EFTAidBean item: aids){
                EFTAid eftAid = new EFTAid();
                eftAid.setAppName(item.getAppNameBytes());
                eftAid.setAid(item.getAidBytes());
                eftAid.setSelFlag(item.getSelFlag());

                eftAid.setTransLimitBytes(item.getTransLimitBytes());
                eftAid.setFloorLimitBytes(item.getFloorLimitBytes());
                eftAid.setCvmLimitBytes(item.getCvmLimitBytes());

                eftAid.setTransLimit(item.getTransLimit());
                eftAid.setTransLimitFlag(item.getTransLimitFlag());
                eftAid.setFloorLimit(item.getFloorLimit());
                eftAid.setFloorLimitFlag(item.getFloorLimitFlag());
                eftAid.setCvmLimit(item.getCvmLimit());
                eftAid.setCvmLimitFlag(item.getCvmLimitFlag());
                eftAid.setTacDenial(item.getTacDenialBytes());
                eftAid.setTacOnline(item.getTacOnlineBytes());
                eftAid.setTacDefault(item.getTacDefaultBytes());
                eftAid.setAcquirerId(item.getAcquirerIdBytes());
                eftAid.setVersion(item.getVersionBytes());
                eftAid.setTermType(item.getTerminalTypeByte());
                eftAid.setTermCapability(item.getTerminalCapabilityBytes());
                eftAid.setTermAddCapability(item.getTerminalAddtionalCapabilityBytes());
                eftAid.setKernelId(item.getKernelIdBytes());
                eftAids.add(eftAid);
            }
            param.setAidList(eftAids);
        }
        return param;
    }

    private DpasParam dpasConvert(DpasParamBean dpasParamBean) {
        List<DpasAidBean> aids = dpasParamBean.getAid();
        DpasParam param = new DpasParam();
        if (aids != null && aids.size() > 0){
            List<DpasAid> dpasAids = new ArrayList<>();
            for (DpasAidBean item: aids){
                DpasAid dpasAid = new DpasAid();
                dpasAid.setAppName(item.getAppNameBytes());
                dpasAid.setAid(item.getAidBytes());
                dpasAid.setSelFlag(item.getSelFlag());
                dpasAid.setTransLimit(item.getTransLimit());
                dpasAid.setTransLimitFlag(item.getTransLimitFlag());
                dpasAid.setFloorLimit(item.getFloorLimit());
                dpasAid.setFloorLimitFlag(item.getFloorLimitFlag());
                dpasAid.setCvmLimit(item.getCvmLimit());
                dpasAid.setCvmLimitFlag(item.getCvmLimitFlag());
                dpasAid.setTacDenial(item.getTacDenialBytes());
                dpasAid.setTacOnline(item.getTacOnlineBytes());
                dpasAid.setTacDefault(item.getTacDefaultBytes());
                dpasAid.setAcquirerId(item.getAcquirerIdBytes());
                dpasAid.setVersion(item.getVersionBytes());
                dpasAid.setTermType(item.getTerminalTypeByte());
                dpasAid.setTermCapability(item.getTerminalCapabilityBytes());
                dpasAid.setExceptFileFlag(item.getExceptFileFlag());
                dpasAid.setTtq(item.getTtqBytes());
                dpasAids.add(dpasAid);
            }
            param.setAidList(dpasAids);
        }
        return param;
    }

    private PayWaveParam paywaveConvert(PayWaveParamBean payWaveParamBean) {
        List<PaywaveAidBean> paywaveAidBeans = payWaveParamBean.getAid();
        List<PaywaveDrlBean> programID = payWaveParamBean.getProgramID();
        PayWaveParam param = new PayWaveParam();
        if (paywaveAidBeans != null && paywaveAidBeans.size() > 0){
            List<PayWaveAid> payWaveAids = new ArrayList<>();
            for (PaywaveAidBean item: paywaveAidBeans){
                PayWaveAid payWaveAid = new PayWaveAid();
                payWaveAid.setAppName(item.getAppNameBytes());
                payWaveAid.setAid(item.getAidBytes());
                payWaveAid.setSelFlag(item.getSelFlag());
                payWaveAid.setVersion(item.getVersionBytes());
                payWaveAid.setTermType(item.getTerminalTypeByte());
                payWaveAid.setTTQ(item.getTtqBytes());
                payWaveAid.setCrypto17Flag(item.getCrypto17Flag());
                payWaveAid.setStatusCheckFlag(item.getStatusCheckFlag());
                payWaveAid.setZeroAmountNoAllowed(item.getAmountZeroNoAllowed());
                payWaveAid.setSecurityCapability(item.getAmountZeroNoAllowed());
                payWaveAid.setDomesticOnly(item.getDomesticOnly());
                payWaveAid.setEnDDAVerNo(item.getEnDDAVerNo());
                payWaveAid.setPayWaveInterFloorLimitList(convertPaywaveFloorLimit(item.getInterWareFloorLimit()));
                payWaveAids.add(payWaveAid);
            }
            param.setAidList(payWaveAids);
        }
        if (programID != null && programID.size() > 0){
            List<PayWaveProgramId> waveProgramIdList = new ArrayList<>();
            for (PaywaveDrlBean item : programID){
                PayWaveProgramId waveProgramId = new PayWaveProgramId();
                waveProgramId.setProgramId(item.getProgramIdBytes());
                waveProgramId.setContactlessTransactionLimit(item.getTransLimit());
                waveProgramId.setContactlessTransactionLimitSupported(item.getTransLimitFlag());
                waveProgramId.setContactlessFloorLimit(item.getFloorLimit());
                waveProgramId.setContactlessFloorLimitSupported(item.getFloorLimitFlag());
                waveProgramId.setContactlessCvmLimit(item.getCvmLimit());
                waveProgramId.setCvmLimitSupported(item.getCvmLimitFlag());
                waveProgramId.setCryptogramVersion17Supported(item.getCrypto17Flag());
                waveProgramId.setZeroAmountNoAllowed(item.getAmountZeroNoAllowed());
                waveProgramId.setStatusCheckSupported(item.getStatusCheckFlag());
                waveProgramId.setReaderTtq(item.getTtqBytes());
                waveProgramIdList.add(waveProgramId);
            }
            param.setWaveProgramIdList(waveProgramIdList);
        }
        return param;
    }

    private List<PayWaveInterFloorLimit> convertPaywaveFloorLimit(List<PayWaveInterFloorLimitBean> interWareFloorLimit) {
        if (interWareFloorLimit == null || interWareFloorLimit.isEmpty()){
            return null;
        }
        List<PayWaveInterFloorLimit> payWaveInterFloorLimits = new ArrayList<>();
        for (PayWaveInterFloorLimitBean item : interWareFloorLimit){
            PayWaveInterFloorLimit payWaveInterFloorLimit = new PayWaveInterFloorLimit();
            payWaveInterFloorLimit.setTransactionType(item.getTransType());
            payWaveInterFloorLimit.setContactlessTransactionLimit(item.getTransLimit());
            payWaveInterFloorLimit.setContactlessTransactionLimitSupported(item.getTransLimitFlag());
            payWaveInterFloorLimit.setContactlessCvmLimit(item.getCvmLimit());
            payWaveInterFloorLimit.setCvmLimitSupported(item.getCvmLimitFlag());
            payWaveInterFloorLimit.setContactlessFloorLimit(item.getFloorLimit());
            payWaveInterFloorLimit.setContactlessFloorLimitSupported(item.getFloorLimitFlag());
            payWaveInterFloorLimits.add(payWaveInterFloorLimit);
        }
        return payWaveInterFloorLimits;
    }


    private PayPassParam paypassConvert(PayPassParamBean payPassParam) {
        List<PayPassAidBean> aids = payPassParam.getAid();
        PayPassParam param = new PayPassParam();
        if (aids != null && aids.size() > 0){
            List<PayPassAid> payPassAidList = new ArrayList<>();
            for (PayPassAidBean item: aids){
                PayPassAid payPassAid = new PayPassAid();
                payPassAid.setAppName(item.getAppNameBytes());
                payPassAid.setAid(item.getAidBytes());
                payPassAid.setSelFlag(item.getSelFlag());

                payPassAid.setTransLimitBytes(item.getTransLimitBytes());
                payPassAid.setCvmLimitBytes(item.getCvmLimitBytes());
                payPassAid.setFloorLimitBytes(item.getFloorLimitBytes());
                payPassAid.setCvmTransLimitBytes(item.getCvmTransLimitBytes());

                payPassAid.setTransLimit(item.getTransLimit());
                payPassAid.setTransLimitFlag(item.getTransLimitFlag());
                payPassAid.setCvmTransLimitBytes(item.getCvmTransLimitBytes());
                payPassAid.setFloorLimit(item.getFloorLimit());
                payPassAid.setFloorLimitFlag(item.getFloorLimitFlag());
                payPassAid.setCvmLimit(item.getCvmLimit());
                payPassAid.setCvmLimitFlag(item.getCvmLimitFlag());
                payPassAid.setTacDenial(item.getTacDenialBytes());
                payPassAid.setTacOnline(item.getTacOnlineBytes());
                payPassAid.setTacDefault(item.getTacDefaultBytes());
                payPassAid.setAcquirerId(item.getAcquirerIdBytes());
                payPassAid.setVersion(item.getVersionBytes());
                payPassAid.setTermRiskManage(item.getRiskManageDataBytes());
                payPassAid.setTermType(item.getTerminalTypeByte());
                payPassAid.setAddCapability(item.getTerminalAdditionalCapabilityBytes());
                payPassAid.setKernelConfig(item.getKernelConfigBytes());
                payPassAid.setCardDataInput(item.getCardDataInputBytes());
                payPassAid.setCvmRequired(item.getCvmRequiredBytes());
                payPassAid.setNoCvmRequired(item.getNoCvmRequiredBytes());
                payPassAid.setSecurityCapability(item.getSecurityCapabilityBytes());
                payPassAid.setMagVersion(item.getMagVersionBytes());
                payPassAid.setMagCvm(item.getMagCvmBytes());
                payPassAid.setMagNoCvm(item.getMagNoCvmBytes());
                payPassAid.setKernelId(item.getKernelIdBytes());
                payPassAid.setDataExchangeSupportFlag(item.getDataExchangeSupportFlag());
                payPassAid.setTlvParam(item.getTlvParamBytes());
                payPassAid.setDefaultUDOL(item.getDefaultUDOLBytes());
                payPassAid.setRefundVoidFloorLimit(item.getRefundVoidTacDenialBytes());
                payPassAid.setRefundVoidTacDenial(item.getRefundVoidTacDenialBytes());
                payPassAid.setMaxTornNum(item.getMaxTornNumBytes());
                payPassAid.setMaxTornLifetime(item.getMaxTornLifetimeBytes());
                payPassAid.setSupportDefaultMcTermParam(item.getSupportDefaultMcTermParam());
                payPassAid.setDeviceSN(item.getDeviceSNBytes());
                payPassAid.setDsOperatorId(item.getDsOperatorIdBytes());
                payPassAidList.add(payPassAid);
            }
            param.setAidList(payPassAidList);
        }
        return param;
    }

    private AmexParam aemxConvert(AmexParamBean amexParam) {
        List<AmexAidBean> aemxAids = amexParam.getAid();
        List<AmexDrlBean> programID = amexParam.getProgramID();
        AmexParam param = new AmexParam();
        if (aemxAids != null && aemxAids.size() > 0){
            List<AmexAid> amexAidList = new ArrayList<>();
            for (AmexAidBean item: aemxAids){
                AmexAid amexAid = new AmexAid();
                amexAid.setAppName(item.getAppNameBytes());
                amexAid.setAid(item.getAidBytes());
                amexAid.setSelFlag(item.getSelFlag());
                amexAid.setTransLimit(item.getTransLimit());
                amexAid.setTransLimitFlag(item.getTransLimitFlag());
                amexAid.setFloorLimit(item.getFloorLimit());
                amexAid.setFloorLimitFlag(item.getFloorLimitFlag());
                amexAid.setCvmLimit(item.getCvmLimit());
                amexAid.setCvmLimitFlag(item.getCvmLimitFlag());
                amexAid.setTacDenial(item.getTacDenialBytes());
                amexAid.setTacOnline(item.getTacOnlineBytes());
                amexAid.setTacDefault(item.getTacDefaultBytes());
                amexAid.setAcquirerId(item.getAcquirerIdBytes());
                amexAid.setdDOL(item.getdDOLBytes());
                amexAid.settDOL(item.gettDOLBytes());
                amexAid.setVersion(item.getVersionBytes());
                amexAid.setTermType(item.getTerminalTypeByte());
                amexAid.setTermCapability(item.getTerminalCapabilityBytes());
                amexAid.setTermAddCapability(item.getTerminalAdditionalCapabilityBytes());
                amexAid.setUnRange(item.getUnRangeBytes());
                amexAid.setSupportOptTrans(item.getSupportOptTrans());
                amexAid.setTermTransCap(item.getTermTransCapBytes());
                amexAid.setDelayAuthSupport(item.getDelayAuthSupport());
                amexAid.setExPayRdCap(item.getExPayRdCapByte());
                amexAid.setExFunction(item.getExFunctionBytes());
                amexAid.setSupportFullOnline(item.getSupportFullOnline());
                amexAid.setAucRFU(item.getAucRFUBytes());
                amexAidList.add(amexAid);
            }
            param.setAidList(amexAidList);
        }
        if (programID != null && programID.size() > 0){
            for (AmexDrlBean item : programID){
                ExPayDRL exPayDRL = new ExPayDRL();
                exPayDRL.setProgramId(item.getProgramIdBytes());
                exPayDRL.setProgramIdLen(item.getProgramIdLen());

                exPayDRL.setRdClssTxnLmt(item.getTransLimitBytes());
                exPayDRL.setRdClssTxnLmtFlg(item.getTransLimitFlag());

                exPayDRL.setRdCVMLmt(item.getCvmLimitBytes());
                exPayDRL.setRdCVMLmtFlg(item.getCvmLimitFlag());

                exPayDRL.setTermFloorLmt(item.getFloorLimitBytes());
                exPayDRL.setTermFloorLmtFlg(item.getFloorLimitFlag());
                exPayDRL.setRdClssFloorLmt(item.getFloorLimitBytes());
                exPayDRL.setRdClssFloorLmtFlg(item.getFloorLimitFlag());

                exPayDRL.setStatusCheckFlg(item.getStatusCheckFlag());
                exPayDRL.setAmtZeroNoAllowed(item.getAmountZeroNoAllowed());
                exPayDRL.setDynamicLimitSet(item.getDynamicLimitSet());
                param.addDrl(exPayDRL);
            }
        }
        return param;
    }

    private List<com.pax.emvbase.param.contact.EmvAid> emvAidConvert(@NonNull List<EmvAid> emvAidList) {
        List<com.pax.emvbase.param.contact.EmvAid> emvAids = new ArrayList<>();
        for (EmvAid item : emvAidList){
            com.pax.emvbase.param.contact.EmvAid emvAid = new com.pax.emvbase.param.contact.EmvAid();
            emvAid.setPartialAIDSelection((byte) item.getSelFlag());
            emvAid.setApplicationID(item.getAidBytes());
            emvAid.setLocalAIDName(item.getAppName());
            emvAid.setPriority((byte) item.getPriority());
            emvAid.setRandTransSel(item.getRandTransSelByte());
            emvAid.setVelocityCheck(item.getVelocityCheckByte());
            emvAid.setAcquirerIdBytes(item.getAcquirerIdBytes());
            emvAid.setTerminalRiskManagementData(item.getRiskManageDataBytes());
            emvAid.setTerminalAIDVersion(item.getVersionBytes());
            emvAid.setTacDenial(item.getTacDenialBytes());
            emvAid.setTacOnline(item.getTacOnlineBytes());
            emvAid.setTacDefault(item.getTacDefaultBytes());
            emvAid.setFloorLimit(item.getFloorLimit());
            emvAid.setThreshold(item.getThreshold());
            emvAid.setTargetPercentage((byte) item.getTargetPer());
            emvAid.setMaxTargetPercentage((byte) item.getMaxTargetPer());
            emvAid.setTerminalDefaultTDOL(item.gettDOLBytes());
            emvAid.setTerminalDefaultDDOL(item.getdDOLBytes());
            emvAid.setTerminalType(item.getTerminalTypeByte());
            emvAid.setTerminalCapability(item.getTerminalCapabilityBytes());
            emvAid.setAdditionalTerminalCapabilities(item.getTerminalAdditionalCapabilityBytes());
            emvAid.setGetDataForPINTryCounter(item.getGetDataPIN());
            emvAid.setBypassPINEntry(item.getBypassPin());
            emvAid.setSubsequentBypassPINEntry(item.getBypassAllFlg());
            emvAid.setForcedOnlineCapability(item.getForceOnline());
            emvAids.add(emvAid);
        }
        return emvAids;
    }

    /**
     * Gets cached emv param
     *
     * @return cached emv param
     */
    @Override
    public EmvProcessParam getCachedEmvParam() {
        EmvProcessParam emvProcessParam = cachedBuilder.create();
        if (emvProcessParam.getCapkParam() != null
                && emvProcessParam.getCapkParam().getCapkList() != null
                && emvProcessParam.getCapkParam().getCapkRevokeList() != null
                && emvProcessParam.getEmvAidList() != null
                && emvProcessParam.getTermConfig() != null && emvProcessParam.getAmexParam() != null
                && emvProcessParam.getDpasParam() != null && emvProcessParam.getEftParam() != null
                && emvProcessParam.getJcbParam() != null && emvProcessParam.getMirParam() != null
                && emvProcessParam.getPayPassParam() != null
                && emvProcessParam.getPayWaveParam() != null && emvProcessParam.getPbocParam() != null
                && emvProcessParam.getPureParam() != null && emvProcessParam.getRuPayParam() != null
        ){
            LogUtils.d(TAG, "Use cached EMV param");
            return emvProcessParam;
        }
        LogUtils.d(TAG, "Reload EMV param data from database");
        //App Restart,need to reFetch data
        List<EmvAid> emvAids = GreendaoHelper.getEmvAidHelper().loadAll();
        if (emvAids != null && emvAids.size() > 0){
            cachedBuilder.setEmvAidList(emvAidConvert(emvAids));
        }
        List<EmvCapk> emvCapks = GreendaoHelper.getEmvCapkHelper().loadAll();
        List<CapkRevokeBean> capkRevokeBeans = CapkRevokeDbHelper.getInstance().loadAll();
        CapkParamBean capkParamBean = new CapkParamBean();
        capkParamBean.setCapkList(emvCapks);
        capkParamBean.setCapkRevokeList(capkRevokeBeans);
        cachedBuilder.setCapkParam(capkConvert(capkParamBean));
        cachedBuilder.setTermConfig(getTerminalConfig());

        List<AmexAidBean> amexAidBeans = AmexAidDbHelper.getInstance().loadAll();
        List<AmexDrlBean> amexDrlBeans = AmexDrlDbHelper.getInstance().loadAll();
        AmexParamBean amexParam = new AmexParamBean();
        amexParam.setAid(amexAidBeans);
        amexParam.setProgramID(amexDrlBeans);
        cachedBuilder.setAmexParam(aemxConvert(amexParam));

        List<PayPassAidBean> payPassAidBeans = PaypassAidDbHelper.getInstance().loadAll();
        PayPassParamBean payPassParamBean = new PayPassParamBean();
        payPassParamBean.setAid(payPassAidBeans);
        cachedBuilder.setPassParam(paypassConvert(payPassParamBean));

        List<PaywaveAidBean> paywaveAidBeans = PaywaveAidDbHelper.getInstance().loadAll();
        List<PaywaveDrlBean> paywaveDrlBeans = PaywaveDrlDbHelper.getInstance().loadAll();
        PayWaveParamBean payWaveParamBean = new PayWaveParamBean();
        payWaveParamBean.setAid(paywaveAidBeans);
        payWaveParamBean.setProgramID(paywaveDrlBeans);
        cachedBuilder.setPayWaveParam(paywaveConvert(payWaveParamBean));

        List<DpasAidBean> dpasAidBeans = DpasAidDbHelper.getInstance().loadAll();
        DpasParamBean dpasParamBean = new DpasParamBean();
        dpasParamBean.setAid(dpasAidBeans);
        cachedBuilder.setDpasParam(dpasConvert(dpasParamBean));

        List<EFTAidBean> eftAidBeans = EFTAidDbHelper.getInstance().loadAll();
        EFTParamBean eftParamBean = new EFTParamBean();
        eftParamBean.setAid(eftAidBeans);
        cachedBuilder.setEFTParam(eftConvert(eftParamBean));

        List<JcbAidBean> jcbAidBeans = JcbAidDbHelper.getInstance().loadAll();
        JcbParamBean jcbParamBean = new JcbParamBean();
        jcbParamBean.setAid(jcbAidBeans);
        cachedBuilder.setJcbParam(jcbConvert(jcbParamBean));

        List<MirAidBean> mirAidBeans = MirAidDbHelper.getInstance().loadAll();
        MirParamBean mirParamBean = new MirParamBean();
        mirParamBean.setAid(mirAidBeans);
        cachedBuilder.setMirParam(mirConvert(mirParamBean));

        List<PBOCAidBean> pbocAidBeans = PBOCAidDbHelper.getInstance().loadAll();
        PBOCParamBean pbocParamBean = new PBOCParamBean();
        pbocParamBean.setAid(pbocAidBeans);
        cachedBuilder.setPbocParam(pbocConvert(pbocParamBean));

        List<PureAidBean> pureAidBeans = PureAidDbHelper.getInstance().loadAll();
        PureParamBean pureParamBean = new PureParamBean();
        pureParamBean.setAid(pureAidBeans);
        cachedBuilder.setPureParam(pureConvert(pureParamBean));

        List<RupayAidBean> rupayAidBeans = RupayAidDbHelper.getInstance().loadAll();
        RupayParamBean rupayParamBean = new RupayParamBean();
        rupayParamBean.setAid(rupayAidBeans);
        cachedBuilder.setRuPayParam(rupayConvert(rupayParamBean));
        return cachedBuilder.create();
    }

    private Config getTerminalConfig() {
        IConfigParamService configParamService = new ConfigParamService();
        IAcquirerIssuerService acquirerIssuerService = new AcquirerIssuerService();

        CurrencyConverter.setDefCurrency(configParamService.getString(ConfigKeyConstant.EDC_CURRENCY_LIST));
        Currency current = Currency.getInstance(CurrencyConverter.getDefCurrency());
        String currency = String.valueOf(
                CountryCode.getByCode(CurrencyConverter.getDefCurrency().getCountry()).getCurrencyNumeric());
        String country = String.valueOf(
                CountryCode.getByCode(CurrencyConverter.getDefCurrency().getCountry()).getNumeric());

        Acquirer curAcq = acquirerIssuerService.findAcquirer(
                configParamService.getString(ConfigKeyConstant.ACQ_NAME)
        );

        Config termConfig = new Config();
        if (curAcq == null) {
            curAcq = acquirerIssuerService.findAllAcquirers().get(0);
        }
        termConfig.setMerchantId(curAcq.getMerchantId().getBytes());
        String nameLocation = String.format(Locale.US, "%s,%s",
                configParamService.getString(ConfigKeyConstant.EDC_MERCHANT_NAME_EN),
                configParamService.getString(ConfigKeyConstant.EDC_MERCHANT_ADDRESS));
        termConfig.setMerchantNameAndLocation(nameLocation);
        termConfig.setMerchantNameAndLocationBytes(nameLocation.getBytes());
        termConfig.setMerchantCategoryCode(ConvertUtils.strToBcdPaddingRight(
                configParamService.getString(ConfigKeyConstant.MERCHANT_CATEGORY_CODE)
        ));
        termConfig.setConversionRatio(configParamService.getInt(ConfigKeyConstant.TRANS_REFER_CURRENCY_CONVERSION));
        termConfig.setTerminalCountryCode(ConvertUtils.strToBcdPaddingLeft(country));
        termConfig.setTransReferenceCurrencyCode(ConvertUtils.strToBcdPaddingLeft(currency));
        termConfig.setTransReferenceCurrencyExponent((byte) current.getDefaultFractionDigits());
        return termConfig;
    }

    private CapkParam capkConvert(CapkParamBean emvCapkList) {
        List<EmvCapk> capkList = emvCapkList.getCapkList();
        List<CapkRevokeBean> capkRevokeList = emvCapkList.getCapkRevokeList();
        CapkParam capkParam = new CapkParam();
        if (capkList != null && capkList.size() > 0){
            ArrayList<Capk> capks = new ArrayList<>();
            for (EmvCapk item: capkList){
                Capk capk = new Capk();
                capk.setRid(item.getrIDBytes());
                capk.setKeyId((byte) item.getKeyID());
                capk.setHashArithmeticIndex((byte) item.getHashInd());
                capk.setRsaArithmeticIndex((byte) item.getArithInd());
                capk.setModule(item.getModuleBytes());
                capk.setModuleLength(item.getModuleBytes().length);
                capk.setExponent(item.getExponentBytes());
                capk.setExponentLength((byte) item.getExponentBytes().length);
                capk.setExpireDate(item.getExpDateBytes());
                capk.setCheckSum(item.getCheckSumBytes());
                capks.add(capk);
            }
            capkParam.setCapkList(capks);
        }
        if (capkRevokeList != null && capkRevokeList.size() > 0){
            ArrayList<CapkRevoke> revokes = new ArrayList<>();
            for (CapkRevokeBean item : capkRevokeList){
                CapkRevoke revoke = new CapkRevoke();
                revoke.setRid(item.getrIDBytes());
                revoke.setKeyId(item.getKeyIDByte());
                revoke.setCertificateSN(item.getCertificateSNBytes());
                revokes.add(revoke);
            }
            capkParam.setCapkRevokeList(revokes);
        }
        return capkParam;
    }

    @Override
    public void invalidCachedEmvParam() {
        cachedBuilder.setCapkParam(null)
                .setEmvAidList(null)
                .setTermConfig(null)
                .setAmexParam(null)
                .setDpasParam(null)
                .setEFTParam(null)
                .setJcbParam(null)
                .setMirParam(null)
                .setPassParam(null)
                .setPayWaveParam(null)
                .setPbocParam(null)
                .setPureParam(null)
                .setRuPayParam(null);
    }
}
