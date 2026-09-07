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

import com.pax.bizentity.db.helper.GreendaoHelper;
import com.pax.bizentity.entity.AcqIssuerRelation;
import com.pax.bizentity.entity.Acquirer;
import com.pax.bizentity.entity.CardRange;
import com.pax.bizentity.entity.Issuer;
import java.util.List;
public class AcquirerIssuerService {
    /**
     * insert acquirers
     *
     * @param acquirerList acquirers
     * @return insert status
     */
    public boolean insertAcquirer(List<Acquirer> acquirerList) {
        return GreendaoHelper.getAcquirerHelper().insert(acquirerList);
    }

    /**
     * find acquirer by name
     *
     * @param acquirerName acquirer name
     * @return acquirer
     */
    public Acquirer findAcquirer(String acquirerName) {
        return GreendaoHelper.getAcquirerHelper().findAcquirer(acquirerName);
    }

    /**
     * find All Acquirers
     */
    public List<Acquirer> findAllAcquirers() {
        return GreendaoHelper.getAcquirerHelper().loadAll();
    }

    /**
     * update acquirer
     *
     * @param acquirer acquirer
     * @return update status
     */
    public boolean updateAcquirer(Acquirer acquirer) {
        return GreendaoHelper.getAcquirerHelper().update(acquirer);
    }

    /**
     * delete all acquirers
     */
    public void deleteAllAcquirer() {
        GreendaoHelper.getAcquirerHelper().deleteAll();
    }

    /**
     * find issuer by pan
     *
     * @param pan pan
     * @return matched issuer
     */
    public Issuer findIssuerByPan(String pan) {
        return null;
    }

    /**
     * check whether issuer is supported under current acquirer
     *
     * @param issuer
     * @return
     */
    public boolean isIssuerSupported(Issuer issuer) {
        return false;
    }

    /**
     * delete all issuers
     */
    public void deleteAllIssuer() {
        GreendaoHelper.getIssuerHelper().deleteAll();
    }

    /**
     * insert issuer list
     *
     * @param issuerList issuer list
     */
    public void insertIssuer(List<Issuer> issuerList) {
        GreendaoHelper.getIssuerHelper().insert(issuerList);
    }

    /**
     * find issuer by name
     *
     * @param issuerName issuer name
     * @return issuer
     */
    public Issuer findIssuer(String issuerName) {
        return GreendaoHelper.getIssuerHelper().findIssuer(issuerName);
    }

    /**
     * find all issuer
     *
     * @return all issuer
     */
    public List<Issuer> findAllIssuers() {
        return GreendaoHelper.getIssuerHelper().loadAll();
    }

    /**
     * update issuer
     *
     * @param issuer issuer
     */
    public void updateIssuer(Issuer issuer) {
        GreendaoHelper.getIssuerHelper().update(issuer);
    }

    /**
     * bind Acquirer and Issuer
     *
     * @param acquirer Acquirer
     * @param issuer   Issuer
     * @return bind result
     */
    public boolean bind(Acquirer acquirer, Issuer issuer) {
        return GreendaoHelper.getAcqIssuerRelationHelper().bindAcqAndIssuer(acquirer, issuer);
    }

    /**
     * check whether Acquirer and Issuer is bind
     *
     * @param root   Acquirer
     * @param issuer Issuer
     * @return bind result
     */
    public boolean isBind(final Acquirer root, final Issuer issuer) {
        boolean success = true;
        try {
            AcqIssuerRelation relation = GreendaoHelper.getAcqIssuerRelationHelper().findRelation(root, issuer);
            if (relation == null) {
                success = false;
            }
        } catch (Exception var5) {
            success = false;
        }
        return success;
    }

    /**
     * bind acquirer and issuer relation
     *
     * @param acqIssuerRelationList acqIssuerRelationList
     * @return bind result
     */
    public boolean insertAcqIssuerRelation(List<AcqIssuerRelation> acqIssuerRelationList) {
        return GreendaoHelper.getAcqIssuerRelationHelper().insert(acqIssuerRelationList);
    }

    /**
     * find all acquirer and issuer relation
     *
     * @return acquirer and issuer relation list
     */
    public List<AcqIssuerRelation> findAllAcqIssuerRelation() {
        return GreendaoHelper.getAcqIssuerRelationHelper().loadAll();
    }

    /**
     * delete all acquirer and issuer relation
     */
    public void deleteAllAcqIssuerRelation() {
        GreendaoHelper.getAcqIssuerRelationHelper().deleteAll();
    }

    /**
     * delete all card range
     */
    public void deleteAllCardRange() {
        GreendaoHelper.getCardRangeHelper().deleteAll();
    }

    /**
     * insert card range
     *
     * @param cardRangeList card range list
     * @return insert result
     */
    public boolean insertCardRange(List<CardRange> cardRangeList) {
        return GreendaoHelper.getCardRangeHelper().insert(cardRangeList);
    }

    /**
     * find all card range
     *
     * @return all card range
     */
    public List<CardRange> findAllCardRanges() {
        return GreendaoHelper.getCardRangeHelper().loadAll();
    }

    /**
     * find matched issuer by acquirer
     *
     * @param curAcq acquirer
     * @return issuer list
     */
    public List<Issuer> lookupIssuersForAcquirer(Acquirer curAcq) {
        return GreendaoHelper.getIssuerHelper().lookupIssuersForAcquirer(curAcq);
    }

    /**
     * find CardRange by pan
     *
     * @param pan pan
     * @return CardRange
     */
    public CardRange findCardRange(String pan) {
        return GreendaoHelper.getCardRangeHelper().findCardRange(pan);
    }
}
