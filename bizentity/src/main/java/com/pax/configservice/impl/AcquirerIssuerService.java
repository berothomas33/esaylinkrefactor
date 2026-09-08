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
     * insert issuer list
     *
     * @param issuerList issuer list
     */
    public void insertIssuer(List<Issuer> issuerList) {
        GreendaoHelper.getIssuerHelper().insert(issuerList);
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
     * insert card range
     *
     * @param cardRangeList card range list
     * @return insert result
     */
    public boolean insertCardRange(List<CardRange> cardRangeList) {
        return GreendaoHelper.getCardRangeHelper().insert(cardRangeList);
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
