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

import com.pax.bizentity.db.dao.CardBinDao;
import com.pax.bizentity.entity.CardBin;
import java.util.List;
import java.util.Locale;

/**
 * card bin database helper
 */
public class CardBinDbHelper extends BaseDaoHelper<CardBin> {
    private static class LazyHolder {
        public static final CardBinDbHelper INSTANCE = new CardBinDbHelper(CardBin.class);
    }

    /**
     * Get singleton instance
     * @return CardBinDbHelper
     */
    public static CardBinDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public CardBinDbHelper(Class<CardBin> entityClass) {
        super(entityClass);
    }

    /**
     * check specific card number is in the card bin table
     * @param cardNo cardNo
     * @return check result
     */
    public final boolean isInCardBinTable(String cardNo) {
        if (cardNo == null || cardNo.isEmpty()) {
            return false;
        }
        List<CardBin> list = getNoSessionQuery().where(CardBinDao.Properties.Bin.like(String.format(
                Locale.US, "%s%%", cardNo))).list();
        return !list.isEmpty();
    }
}
