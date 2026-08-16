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
 * Date                  Author	                 Action
 * 20190621  	         xieYb                   Create
 * ===========================================================================================
 */
package com.pax.bizentity.db.helper.upgrade.history;

import android.database.SQLException;
import com.alibaba.fastjson.TypeReference;
import com.pax.bizentity.db.dao.AcqIssuerRelationDao;
import com.pax.bizentity.db.dao.AcquirerDao;
import com.pax.bizentity.db.dao.AmexAidBeanDao;
import com.pax.bizentity.db.dao.AmexDrlBeanDao;
import com.pax.bizentity.db.dao.CapkRevokeBeanDao;
import com.pax.bizentity.db.dao.CardBinDao;
import com.pax.bizentity.db.dao.CardRangeDao;
import com.pax.bizentity.db.dao.ClssTornLogDao;
import com.pax.bizentity.db.dao.DpasAidBeanDao;
import com.pax.bizentity.db.dao.EFTAidBeanDao;
import com.pax.bizentity.db.dao.EmvAidDao;
import com.pax.bizentity.db.dao.EmvCapkDao;
import com.pax.bizentity.db.dao.IssuerDao;
import com.pax.bizentity.db.dao.JcbAidBeanDao;
import com.pax.bizentity.db.dao.MirAidBeanDao;
import com.pax.bizentity.db.dao.PBOCAidBeanDao;
import com.pax.bizentity.db.dao.PayPassAidBeanDao;
import com.pax.bizentity.db.dao.PayWaveInterFloorLimitBeanDao;
import com.pax.bizentity.db.dao.PaywaveAidBeanDao;
import com.pax.bizentity.db.dao.PaywaveDrlBeanDao;
import com.pax.bizentity.db.dao.PureAidBeanDao;
import com.pax.bizentity.db.dao.RupayAidBeanDao;
import com.pax.bizentity.db.dao.TransDataDao;
import com.pax.bizentity.db.dao.TransTotalDao;
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
import com.pax.bizentity.db.helper.upgrade.DbUpgrade;
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
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.json.JsonProxy;
import com.pax.commonlib.utils.LogUtils;
import java.util.List;
import org.greenrobot.greendao.database.Database;

/**
 * update database from version 5 to 6
 */
public class Upgrade5To6 extends DbUpgrade {

    @Override
    protected void upgrade(Database db)  {
        BaseApplication.getAppContext().runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    DbUpgrade.upgradeTable(db, AcquirerDao.class,
                            IssuerDao.class,
                            AcqIssuerRelationDao.class,
                            CardBinDao.class,
                            CardRangeDao.class,
                            ClssTornLogDao.class,
                            EmvAidDao.class,
                            EmvCapkDao.class,
                            TransDataDao.class,
                            TransTotalDao.class,
                            CapkRevokeBeanDao.class,
                            AmexAidBeanDao.class,
                            AmexDrlBeanDao.class,
                            DpasAidBeanDao.class,
                            EFTAidBeanDao.class,
                            JcbAidBeanDao.class,
                            MirAidBeanDao.class,
                            PayPassAidBeanDao.class,
                            PaywaveAidBeanDao.class,
                            PaywaveDrlBeanDao.class,
                            PayWaveInterFloorLimitBeanDao.class,
                            PBOCAidBeanDao.class,
                            PureAidBeanDao.class,
                            RupayAidBeanDao.class);

                } catch (SQLException e) {
                    LogUtils.e(TAG,e);
                }
                //init emv params
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
                if (capkParamBean != null){
                    List<EmvCapk> capkList = capkParamBean.getCapkList();
                    List<CapkRevokeBean> capkRevokeList = capkParamBean.getCapkRevokeList();
                    if (capkList != null){
                        GreendaoHelper.getEmvCapkHelper().deleteAll();
                        GreendaoHelper.getEmvCapkHelper().insert(capkList);
                    }
                    if (capkRevokeList != null){
                        CapkRevokeDbHelper.getInstance().insert(capkRevokeList);
                    }
                }
                if (emvAids != null){
                    GreendaoHelper.getEmvAidHelper().deleteAll();
                    GreendaoHelper.getEmvAidHelper().insert(emvAids);
                }

                List<AmexAidBean> amexAidBeanList = amexParamBean.getAid();
                List<AmexDrlBean> amexDrlBeanList = amexParamBean.getProgramID();
                if (amexAidBeanList != null){
                    AmexAidDbHelper.getInstance().insert(amexAidBeanList);
                }
                if (amexDrlBeanList != null){
                    AmexDrlDbHelper.getInstance().insert(amexDrlBeanList);
                }

                List<PayPassAidBean> payPassAidBeanList = payPassParamBean.getAid();
                if (payPassAidBeanList != null){
                    PaypassAidDbHelper.getInstance().insert(payPassAidBeanList);
                }

                List<PaywaveAidBean> paywaveAidBeanList = payWaveParamBean.getAid();
                List<PaywaveDrlBean> paywaveDrlBeanList = payWaveParamBean.getProgramID();
                if (paywaveAidBeanList != null){
                    PaywaveAidDbHelper.getInstance().insert(paywaveAidBeanList);
                    for (PaywaveAidBean item : paywaveAidBeanList){
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
                if (paywaveDrlBeanList != null){
                    PaywaveDrlDbHelper.getInstance().insert(paywaveDrlBeanList);
                }

                List<DpasAidBean> dpasAidBeanList = dpasParamBean.getAid();
                if (dpasAidBeanList != null){
                    DpasAidDbHelper.getInstance().insert(dpasAidBeanList);
                }

                List<EFTAidBean> eftAidBeanList = eftParamBean.getAid();
                if (eftAidBeanList != null){
                    EFTAidDbHelper.getInstance().insert(eftAidBeanList);
                }

                List<JcbAidBean> jcbAidBeanList = jcbParamBean.getAid();
                if (jcbAidBeanList != null){
                    JcbAidDbHelper.getInstance().insert(jcbAidBeanList);
                }

                List<MirAidBean> mirAidBeanList = mirParamBean.getAid();
                if (mirAidBeanList != null){
                    MirAidDbHelper.getInstance().insert(mirAidBeanList);
                }

                List<PBOCAidBean> pbocAidBeanList = pbocParamBean.getAid();
                if (pbocAidBeanList != null){
                    PBOCAidDbHelper.getInstance().insert(pbocAidBeanList);
                }

                List<PureAidBean> pureAidBeanList = pureParamBean.getAid();
                if (pureAidBeanList != null){
                    PureAidDbHelper.getInstance().insert(pureAidBeanList);
                }

                List<RupayAidBean> rupayAidBeanList = rupayParamBean.getAid();
                if (rupayAidBeanList != null){
                    RupayAidDbHelper.getInstance().insert(rupayAidBeanList);
                }
            }
        });
    }
}
