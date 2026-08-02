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

import com.pax.bizentity.entity.Issuer;
import java.util.List;

public interface IConfigIssuer {
    /**
     * find issuer by pan
     * @param pan pan
     * @return matched issuer
     */
    public Issuer findIssuerByPan(final String pan);

    /**
     * check whether issuer is supported under current acquirer
     * @param issuer
     * @return
     */
    public boolean isIssuerSupported(final Issuer issuer);

    /**
     * delete all issuers
     */
    public void deleteAllIssuer();
    /**
     * insert issuer list
     * @param issuerList issuer list
     */
    public void insertIssuer(List<Issuer> issuerList);
    /**
     * find issuer by name
     * @param issuerName issuer name
     * @return issuer
     */
    public Issuer findIssuer(final String issuerName);
    /**
     * find all issuer
     * @return all issuer
     */
    public List<Issuer> findAllIssuers();
    /**
     * update issuer
     * @param issuer issuer
     */
    public void updateIssuer(final Issuer issuer);
}
