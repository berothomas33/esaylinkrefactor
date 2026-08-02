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
 * 20210513 	        xieYb                  Create
 * ===========================================================================================
 *
 */
package com.pax.bizlib.trans;

import android.text.TextUtils;
import com.pax.bizentity.entity.AcqIssuerRelation;
import com.pax.bizentity.entity.Acquirer;
import com.pax.bizentity.entity.CardRange;
import com.pax.bizentity.entity.Issuer;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.export.ConfigKeyConstant;
import com.pax.configservice.export.ConfigServiceConstant;
import com.pax.configservice.export.IAcquirerIssuerService;
import com.pax.configservice.export.IConfigParamService;
import com.sankuai.waimai.router.Router;
import java.util.List;

/**
 * Acquirer Manager
 */
public class AcqManager {

    private static final String TAG = "AcqManager";
    private static AcqManager acqmanager;
    private Acquirer acquirer;
    private final IConfigParamService configParamService = Router.getService(IConfigParamService.class, ConfigServiceConstant.CONFIGSERVICE_CONFIG);
    private final IAcquirerIssuerService acquirerIssuerService =  Router.getService(IAcquirerIssuerService.class, ConfigServiceConstant.CONFIGSERVICE_ACQ_ISSUER);


    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static synchronized AcqManager getInstance() {
        if (acqmanager == null) {
            acqmanager = new AcqManager();
        }
        return acqmanager;
    }

    /**
     * Sets cur acq.
     *
     * @param acq the acq
     */
    public void setCurAcq(Acquirer acq) {
        acquirer = acq;
    }

    /**
     * Gets cur acq.
     *
     * @return the cur acq
     */
    public Acquirer getCurAcq() {
        if (acquirer == null){
            String name = configParamService.getString(ConfigKeyConstant.ACQ_NAME);
            if (!TextUtils.isEmpty(name)) {
                Acquirer acquirer = acquirerIssuerService.findAcquirer(name);
                if (acquirer != null) {
                    acqmanager.setCurAcq(acquirer);
                } else {
                    setDefaultAcq();
                }
            } else {
                setDefaultAcq();
            }
        }
        return acquirer;
    }

    private void setDefaultAcq() {
        List<Acquirer> allAcquirers = acquirerIssuerService.findAllAcquirers();
        if (allAcquirers != null && allAcquirers.size() > 0){
            Acquirer acquirer = allAcquirers.get(0);
            acqmanager.setCurAcq(acquirer);
            configParamService.putString(ConfigKeyConstant.ACQ_NAME,acquirer.getName());
        }
    }

    /**
     * check whether issuer is supported
     *
     * @param issuer issuer
     * @return result boolean
     */
    public boolean isIssuerSupported(final Issuer issuer) {
        try {
            List<Issuer> issuers = acquirerIssuerService.lookupIssuersForAcquirer(getCurAcq());
            for (Issuer tmp : issuers) {
                if (tmp.getName().equals(issuer.getName())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LogUtils.e(TAG, "", e);
        }
        return false;
    }

    /**
     * find issuer by pan
     *
     * @param pan card pan
     * @return issuer issuer
     */
    public Issuer findIssuerByPan(final String pan) {
        if (pan == null || pan.isEmpty()) {
            return null;
        }
        CardRange cardRange =  acquirerIssuerService.findCardRange(pan);
        if (cardRange == null) {
            return null;
        } else {
            return cardRange.getIssuer();
        }
    }

    /**
     * delete all issuer
     */
    public void deleteAllIssuer() {
        acquirerIssuerService.deleteAllIssuer();
    }

    /**
     * insert acquirer list
     *
     * @param acquirerList acquirer list
     * @return insert result
     */
    public boolean insertAcquirer(List<Acquirer> acquirerList) {
        return acquirerIssuerService.insertAcquirer(acquirerList);
    }

    /**
     * find acquirer by name
     *
     * @param acquirerName acquirer name
     * @return acquirer acquirer
     */
    public Acquirer findAcquirer(final String acquirerName) {
        return acquirerIssuerService.findAcquirer(acquirerName);
    }

    /**
     * find all aqcuirers
     *
     * @return all aqcuirers
     */
    public List<Acquirer> findAllAcquirers() {
        return acquirerIssuerService.findAllAcquirers();
    }

    /**
     * update acquirer
     *
     * @param acquirer acquirer
     * @return update result
     */
    public boolean updateAcquirer(final Acquirer acquirer) {
       return acquirerIssuerService.updateAcquirer(acquirer);
    }

    /**
     * delete all acquirer
     */
    public void deleteAllAcquirer() {
        acquirerIssuerService.deleteAllAcquirer();
    }

    /**
     * delete all card range
     */
    public void deleteAllCardRange() {
        acquirerIssuerService.deleteAllCardRange();
    }

    /**
     * find all acquirer and issuer relation
     *
     * @return acquirer and issuer relation list
     */
    public List<AcqIssuerRelation> findAllAcqIssuerRelation() {
        return acquirerIssuerService.findAllAcqIssuerRelation();
    }

    /**
     * delete all acquirer and issuer relation
     */
    public void deleteAllAcqIssuerRelation() {
        acquirerIssuerService.deleteAllAcqIssuerRelation();
    }

    /**
     * insert issuer list
     *
     * @param issuerList issuer list
     */
    public void insertIssuer(List<Issuer> issuerList) {
        acquirerIssuerService.insertIssuer(issuerList);
    }

    /**
     * find issuer by name
     *
     * @param issuerName issuer name
     * @return issuer issuer
     */
    public Issuer findIssuer(final String issuerName) {
        return acquirerIssuerService.findIssuer(issuerName);
    }

    /**
     * find all issuer
     *
     * @return all issuer
     */
    public List<Issuer> findAllIssuers() {
        return acquirerIssuerService.findAllIssuers();
    }

    /**
     * bind Acquirer and Issuer
     *
     * @param root   Acquirer
     * @param issuer Issuer
     * @return bind result
     */
    public boolean bind(final Acquirer root, final Issuer issuer) {
        return acquirerIssuerService.bind(root, issuer);
    }

    /**
     * check whether Acquirer and Issuer is bind
     *
     * @param root   Acquirer
     * @param issuer Issuer
     * @return bind result
     */
    public boolean isBind(final Acquirer root, final Issuer issuer) {
        return acquirerIssuerService.isBind(root, issuer);
    }

    /**
     * bind acquirer and issuer relation
     *
     * @param acqIssuerRelationList acqIssuerRelationList
     * @return bind result
     */
    public boolean insertAcqIssuerRelation(List<AcqIssuerRelation> acqIssuerRelationList) {
        return acquirerIssuerService.insertAcqIssuerRelation(acqIssuerRelationList);
    }

    /**
     * update issuer
     *
     * @param issuer issuer
     */
    public void updateIssuer(final Issuer issuer) {
        acquirerIssuerService.updateIssuer(issuer);
    }

    /**
     * insert card range
     *
     * @param cardRangeList card range list
     * @return insert result
     */
    public boolean insertCardRange(List<CardRange> cardRangeList) {
        return acquirerIssuerService.insertCardRange(cardRangeList);
    }

    /**
     * find all card range
     *
     * @return all card range
     */
    public List<CardRange> findAllCardRanges() {
        return acquirerIssuerService.findAllCardRanges();
    }
}
