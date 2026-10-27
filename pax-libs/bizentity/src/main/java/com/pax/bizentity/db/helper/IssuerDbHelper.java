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
 * 20210508 	        xieYb                  Create
 * ===========================================================================================
 *
 */
package com.pax.bizentity.db.helper;

import com.pax.bizentity.db.dao.IssuerDao;
import com.pax.bizentity.entity.Issuer;

/**
 * Database operation helper of Issuer
 */
public class IssuerDbHelper extends BaseDaoHelper<Issuer> {
    private static class LazyHolder {
        public static final IssuerDbHelper INSTANCE = new IssuerDbHelper(Issuer.class);
    }

    public static IssuerDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public IssuerDbHelper(Class<Issuer> entityClass) {
        super(entityClass);
    }

    public final Issuer findIssuer(String issuerName) {
        if (issuerName == null || issuerName.isEmpty()) {
            return null;
        }
        return getNoSessionQuery().where(IssuerDao.Properties.Name.eq(issuerName)).unique();
    }

    public final Issuer findIssuerByIssuerId(long issuerId) {
        return getNoSessionQuery().where(IssuerDao.Properties.Id.eq(issuerId)).unique();
    }

}
