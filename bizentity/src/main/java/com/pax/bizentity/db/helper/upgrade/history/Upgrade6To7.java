package com.pax.bizentity.db.helper.upgrade.history;

import com.alibaba.fastjson.TypeReference;
import com.pax.bizentity.db.dao.DpasAidBeanDao;
import com.pax.bizentity.db.dao.MirAidBeanDao;
import com.pax.bizentity.db.dao.PBOCAidBeanDao;
import com.pax.bizentity.db.dao.PayPassAidBeanDao;
import com.pax.bizentity.db.dao.RupayAidBeanDao;
import com.pax.bizentity.db.helper.DpasAidDbHelper;
import com.pax.bizentity.db.helper.MirAidDbHelper;
import com.pax.bizentity.db.helper.PBOCAidDbHelper;
import com.pax.bizentity.db.helper.PaypassAidDbHelper;
import com.pax.bizentity.db.helper.RupayAidDbHelper;
import com.pax.bizentity.db.helper.upgrade.DbUpgrade;
import com.pax.bizentity.db.helper.upgrade.UpgradeConst;
import com.pax.bizentity.entity.clss.dpas.DpasAidBean;
import com.pax.bizentity.entity.clss.dpas.DpasParamBean;
import com.pax.bizentity.entity.clss.mir.MirAidBean;
import com.pax.bizentity.entity.clss.mir.MirParamBean;
import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;
import com.pax.bizentity.entity.clss.paypass.PayPassParamBean;
import com.pax.bizentity.entity.clss.pboc.PBOCAidBean;
import com.pax.bizentity.entity.clss.pboc.PBOCParamBean;
import com.pax.bizentity.entity.clss.rupay.RupayAidBean;
import com.pax.bizentity.entity.clss.rupay.RupayParamBean;
import com.pax.commonlib.json.JsonProxy;
import com.pax.commonlib.utils.LogUtils;
import com.sankuai.waimai.router.annotation.RouterService;
import java.util.List;
import org.greenrobot.greendao.database.Database;

/**
 * File description
 *
 * @author yehongbo
 * @date 2021/11/25
 */
@RouterService(interfaces = DbUpgrade.class, key = UpgradeConst.UPGRADE_6_7)
public class Upgrade6To7 extends DbUpgrade {
    private static final String TAG = "Upgrade6To7";

    @Override
    protected void upgrade(Database db) {
        try {
            DbUpgrade.upgradeTable(db, DpasAidBeanDao.class, PBOCAidBeanDao.class,
                    MirAidBeanDao.class, PayPassAidBeanDao.class, RupayAidBeanDao.class);
        } catch (Exception e) {
            LogUtils.e(TAG, "", e);
        }

        addAfterDBReadyCallback(() -> {
            LogUtils.d(TAG, "insert new data");
            DpasParamBean dpasParamBean = JsonProxy.getInstance().readObjFromAsset("dpas.json",new TypeReference<DpasParamBean>(){}.getType());
            List<DpasAidBean> dpasAidBeanList = dpasParamBean.getAid();
            if (dpasAidBeanList != null){
                DpasAidDbHelper.getInstance().deleteAll();
                DpasAidDbHelper.getInstance().insert(dpasAidBeanList);
            }

            MirParamBean mirParamBean = JsonProxy.getInstance().readObjFromAsset("mir.json",new TypeReference<MirParamBean>(){}.getType());
            List<MirAidBean> mirAidBeanList = mirParamBean.getAid();
            if (mirAidBeanList != null){
                MirAidDbHelper.getInstance().deleteAll();
                MirAidDbHelper.getInstance().insert(mirAidBeanList);
            }

            PayPassParamBean payPassParamBean = JsonProxy.getInstance().readObjFromAsset("paypass.json",new TypeReference<PayPassParamBean>(){}.getType());
            List<PayPassAidBean> payPassAidBeanList = payPassParamBean.getAid();
            if (payPassAidBeanList != null){
                PaypassAidDbHelper.getInstance().deleteAll();
                PaypassAidDbHelper.getInstance().insert(payPassAidBeanList);
            }

            PBOCParamBean pbocParamBean = JsonProxy.getInstance().readObjFromAsset("pboc.json",new TypeReference<PBOCParamBean>(){}.getType());
            List<PBOCAidBean> pbocAidBeanList = pbocParamBean.getAid();
            if (pbocAidBeanList != null){
                PBOCAidDbHelper.getInstance().deleteAll();
                PBOCAidDbHelper.getInstance().insert(pbocAidBeanList);
            }

            RupayParamBean rupayParamBean = JsonProxy.getInstance().readObjFromAsset("rupay.json",new TypeReference<RupayParamBean>(){}.getType());
            List<RupayAidBean> rupayAidBeanList = rupayParamBean.getAid();
            if (rupayAidBeanList != null){
                RupayAidDbHelper.getInstance().deleteAll();
                RupayAidDbHelper.getInstance().insert(rupayAidBeanList);
            }
        });
    }
}
