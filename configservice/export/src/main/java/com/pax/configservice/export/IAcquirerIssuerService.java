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

package com.pax.configservice.export;

import com.pax.bizentity.entity.AcqIssuerRelation;
import com.pax.bizentity.entity.Acquirer;
import com.pax.bizentity.entity.CardRange;
import com.pax.bizentity.entity.Issuer;
import java.util.List;

 public interface IAcquirerIssuerService {
    /**
     * insert acquirers
     * @param acquirerList acquirers
     * @return insert status
     */
     boolean insertAcquirer(List<Acquirer> acquirerList);

    /**
     * find acquirer by name
     * @param acquirerName acquirer name
     * @return acquirer
     */
     Acquirer findAcquirer(final String acquirerName);
    /**
     * find All Acquirers
     */
     List<Acquirer> findAllAcquirers();

    /**
     * update acquirer
     * @param acquirer acquirer
     * @return update status
     */
     boolean updateAcquirer(final Acquirer acquirer);

    /**
     * delete all acquirers
     */
     void deleteAllAcquirer();

    /**
     * find issuer by pan
     * @param pan pan
     * @return matched issuer
     */
     Issuer findIssuerByPan(final String pan);

    /**
     * check whether issuer is supported under current acquirer
     * @param issuer
     * @return
     */
     boolean isIssuerSupported(final Issuer issuer);

    /**
     * delete all issuers
     */
    void deleteAllIssuer();
    /**
     * insert issuer list
     * @param issuerList issuer list
     */
    void insertIssuer(List<Issuer> issuerList);
    /**
     * find issuer by name
     * @param issuerName issuer name
     * @return issuer
     */
    Issuer findIssuer(final String issuerName);
    /**
     * find all issuer
     * @return all issuer
     */
    List<Issuer> findAllIssuers();
    /**
     * update issuer
     * @param issuer issuer
     */
     void updateIssuer(final Issuer issuer);

    /**
     * bind Acquirer and Issuer
     * @param acquirer   Acquirer
     * @param issuer Issuer
     * @return bind result
     */
    boolean bind(final Acquirer acquirer, final Issuer issuer);
    /**
     * check whether Acquirer and Issuer is bind
     * @param root   Acquirer
     * @param issuer Issuer
     * @return bind result
     */
    boolean isBind(final Acquirer root, final Issuer issuer);
    /**
     * bind acquirer and issuer relation
     *
     * @param acqIssuerRelationList acqIssuerRelationList
     * @return bind result
     */
    boolean insertAcqIssuerRelation(List<AcqIssuerRelation> acqIssuerRelationList);
    /**
     * find all acquirer and issuer relation
     * @return acquirer and issuer relation list
     */
    List<AcqIssuerRelation> findAllAcqIssuerRelation();
    /**
     * delete all acquirer and issuer relation
     */
    void deleteAllAcqIssuerRelation();

    /**
     * delete all card range
     */
    void deleteAllCardRange();
    /**
     * insert card range
     * @param cardRangeList card range list
     * @return insert result
     */
    boolean insertCardRange(List<CardRange> cardRangeList);
    /**
     * find all card range
     * @return all card range
     */
    List<CardRange> findAllCardRanges();

     /**
      * find matched issuer by acquirer
      * @param curAcq acquirer
      * @return issuer list
      */
    List<Issuer> lookupIssuersForAcquirer(Acquirer curAcq);

     /**
      * find CardRange by pan
      * @param pan pan
      * @return CardRange
      */
     CardRange findCardRange(String pan);
 }
