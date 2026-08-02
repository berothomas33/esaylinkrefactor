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

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.CardRangeDao;
import com.pax.bizentity.entity.CardRange;
import com.pax.bizentity.entity.Issuer;
import java.util.List;

public class CardRangeDbHelper extends BaseDaoHelper<CardRange> {
    private static class LazyHolder {
        public static final CardRangeDbHelper INSTANCE = new CardRangeDbHelper(CardRange.class);
    }

    public static CardRangeDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public CardRangeDbHelper(Class<CardRange> entityClass) {
        super(entityClass);
    }

    public final CardRange findCardRange(Long lowLimit, Long highLimit) {
        return (CardRange) getNoSessionQuery().where(CardRangeDao.Properties.PanRangeLow.eq(lowLimit),
                CardRangeDao.Properties.PanRangeHigh.eq(highLimit))
                .unique();
    }

    /**
     * WHERE (low <= ? AND high >= ?) @1
     * WHERE (length = 0 OR ? = length) @2
     * WHERE @1 AND @2
     * order by (high - low)
     */
    @Nullable
    public final CardRange findCardRange(String pan) {
        if (pan.length() < 10){
            return null;
        }
        String subPan = pan.substring(0, 10);
        if (subPan.contains("*")){
            //p2pe mode is masked pan
            subPan = subPan.replaceAll("[*]", "0");
        }
        List<CardRange> list = getNoSessionQuery().where(CardRangeDao.Properties.PanRangeLow.le(subPan),
                CardRangeDao.Properties.PanRangeHigh.ge(subPan))
                .whereOr(CardRangeDao.Properties.PanLength.eq(0)
                        , CardRangeDao.Properties.PanLength.eq(pan.length()))
                .orderRaw(CardRangeDao.Properties.PanRangeHigh.columnName + "-" + CardRangeDao.Properties.PanRangeLow.columnName)
                .list();
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    public final List<CardRange> findCardRange(Issuer issuer) {
        return getNoSessionQuery().where(CardRangeDao.Properties.Issuer_id.eq(issuer.getId())).list();
    }

}
