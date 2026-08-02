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
 * 20210507 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.configservice.impl;

import com.alibaba.fastjson.TypeReference;
import com.pax.bizentity.entity.AcqIssuerRelation;
import com.pax.bizentity.entity.Acquirer;
import com.pax.bizentity.entity.CapkParamBean;
import com.pax.bizentity.entity.CardRange;
import com.pax.bizentity.entity.EmvAid;
import com.pax.bizentity.entity.Issuer;
import com.pax.bizentity.entity.clss.amex.AmexParamBean;
import com.pax.bizentity.entity.clss.dpas.DpasParamBean;
import com.pax.bizentity.entity.clss.eft.EFTParamBean;
import com.pax.bizentity.entity.clss.jcb.JcbParamBean;
import com.pax.bizentity.entity.clss.mir.MirParamBean;
import com.pax.bizentity.entity.clss.paypass.PayPassParamBean;
import com.pax.bizentity.entity.clss.paywave.PayWaveParamBean;
import com.pax.bizentity.entity.clss.pboc.PBOCParamBean;
import com.pax.bizentity.entity.clss.pure.PureParamBean;
import com.pax.bizentity.entity.clss.rupay.RupayParamBean;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.init.IModuleInit;
import com.pax.commonlib.json.JsonProxy;
import com.pax.commonlib.utils.RxUtils;
import com.pax.configservice.export.ConfigServiceConstant;
import com.pax.configservice.export.IAcquirerIssuerService;
import com.pax.configservice.export.IConfigParamService;
import com.pax.configservice.export.IEmvParamService;
import com.pax.poslib.model.ModelInfo;
import com.sankuai.waimai.router.Router;
import com.sankuai.waimai.router.annotation.RouterService;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * init all required parameters by application
 */
@RouterService(interfaces = IModuleInit.class,key = ConfigServiceConstant.INIT_CONFIG)
public class ConfigInit implements IModuleInit {
    private IModuleInit.Callback callback;

    @Override
    public void init() {
        Subject<Boolean> configSubject = BehaviorSubject.createDefault(false);
        Subject<Boolean> acquirerSubject = BehaviorSubject.createDefault(false);

        BaseApplication.getAppContext().runInBackground(new Runnable() {
            @Override
            public void run() {
                HashMap<String, String> paramMap = JsonProxy.getInstance().readObjFromAsset("config.json");
                IConfigParamService configParamService = Router.getService(IConfigParamService.class, ConfigServiceConstant.CONFIGSERVICE_CONFIG);
                Set<Map.Entry<String, String>> entries = paramMap.entrySet();
                for (Map.Entry<String, String> entry: entries){
                    configParamService.putString(entry.getKey(),entry.getValue());
                }
                configSubject.onNext(true);

                if (callback != null) {
                    callback.initDone();
                    callback = null;
                }
            }
        });

        BaseApplication.getAppContext().runInBackground(new Runnable() {
            @Override
            public void run() {
                List<Acquirer> acquirers = JsonProxy.getInstance().readObjFromAsset("acquirer.json",new TypeReference<List<Acquirer>>(){}.getType());
                List<Issuer> issuers = JsonProxy.getInstance().readObjFromAsset("issuer.json",new TypeReference<List<Issuer>>(){}.getType());
                List<CardRange> cardRanges = JsonProxy.getInstance().readObjFromAsset("card_range.json",new TypeReference<List<CardRange>>(){}.getType());
                IAcquirerIssuerService acquirerIssuerService = Router.getService(IAcquirerIssuerService.class, ConfigServiceConstant.CONFIGSERVICE_ACQ_ISSUER);
                acquirerIssuerService.insertIssuer(issuers);
                // Check does this device supports QR code scanning
                if (!(ModelInfo.getInstance().isSupportCamera() || ModelInfo.getInstance()
                        .isSupportScannerHw())) {
                    for (Acquirer acquirer : acquirers) {
                        acquirer.setEnableQR(false);
                    }
                }
                acquirerSubject.onNext(acquirerIssuerService.insertAcquirer(acquirers));
                List<AcqIssuerRelation> acqIssuerRelationList = new ArrayList<>();
                for (Issuer issuer : issuers) {
                    for (Acquirer acquirer : acquirers) {
                        if (!acquirerIssuerService.isBind(acquirer,issuer)){
                            acqIssuerRelationList.add(new AcqIssuerRelation(acquirer,issuer));
                        }
                    }
                    for (CardRange cardRange : cardRanges) {
                        if (cardRange.getName().equals(issuer.getName())) {
                            cardRange.setIssuer(issuer);
                        }
                    }
                }
                acquirerIssuerService.insertCardRange(cardRanges);
                acquirerIssuerService.insertAcqIssuerRelation(acqIssuerRelationList);
            }
        });
        // init emv params
        // Whether it is standard EMV or P2PE EMV, all parameters will be saved in the database,
        // even if the P2PE EMV not support some of kernel.
       BaseApplication.getAppContext().runInBackground(new Runnable() {
           @Override
           public void run() {
               CapkParamBean capkParamBean = JsonProxy.getInstance().readObjFromAsset("capk.json",new TypeReference<CapkParamBean>(){}.getType());
               List<EmvAid> emvAids = JsonProxy.getInstance().readObjFromAsset("contactAid.json",new TypeReference<List<EmvAid>>(){}.getType());
               AmexParamBean amexParamBean = JsonProxy.getInstance().readObjFromAsset("amex.json",new TypeReference<AmexParamBean>(){}.getType());
               PayPassParamBean payPassParamBean = JsonProxy.getInstance().readObjFromAsset("paypass.json",new TypeReference<PayPassParamBean>(){}.getType());
               PayWaveParamBean payWaveParamBean = JsonProxy.getInstance().readObjFromAsset("paywave.json",new TypeReference<PayWaveParamBean>(){}.getType());
               DpasParamBean dpasParamBean = JsonProxy.getInstance().readObjFromAsset("dpas.json",new TypeReference<DpasParamBean>(){}.getType());
               EFTParamBean eftParamBean = JsonProxy.getInstance().readObjFromAsset("eft.json",new TypeReference<EFTParamBean>(){}.getType());
               JcbParamBean jcbParamBean = JsonProxy.getInstance().readObjFromAsset("jcb.json",new TypeReference<JcbParamBean>(){}.getType());
               MirParamBean mirParamBean = JsonProxy.getInstance().readObjFromAsset("mir.json",new TypeReference<MirParamBean>(){}.getType());
               PBOCParamBean pbocParamBean = JsonProxy.getInstance().readObjFromAsset("pboc.json",new TypeReference<PBOCParamBean>(){}.getType());
               PureParamBean pureParamBean = JsonProxy.getInstance().readObjFromAsset("pure.json",new TypeReference<PureParamBean>(){}.getType());
               RupayParamBean rupayParamBean = JsonProxy.getInstance().readObjFromAsset("rupay.json",new TypeReference<RupayParamBean>(){}.getType());
               IEmvParamService emvParamService = Router.getService(IEmvParamService.class, ConfigServiceConstant.CONFIGSERVICE_EMVPARAM);
               RxUtils.addDisposable(
                       Observable.combineLatest(configSubject, acquirerSubject, (b1, b2) -> b1 && b2)
                       .subscribe(b -> {
                           // To ensure that config and acquirer have been initialized before initializing terminalConfig
                           if (b) {
                               emvParamService.insertTerminalConfig();
                               RxUtils.release();
                           }
                       }));
               emvParamService.insertEmvAid(emvAids);
               emvParamService.insertEmvCapk(capkParamBean);
               emvParamService.insertAmexParam(amexParamBean);
               emvParamService.insertPaypassParam(payPassParamBean);
               emvParamService.insertPaywaveParam(payWaveParamBean);
               emvParamService.insertDpasParam(dpasParamBean);
               emvParamService.insertEFTParam(eftParamBean);
               emvParamService.insertJcbParam(jcbParamBean);
               emvParamService.insertMirParam(mirParamBean);
               emvParamService.insertPBOCParam(pbocParamBean);
               emvParamService.insertPureParam(pureParamBean);
               emvParamService.insertRupayParam(rupayParamBean);
           }
       });

    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }
}
