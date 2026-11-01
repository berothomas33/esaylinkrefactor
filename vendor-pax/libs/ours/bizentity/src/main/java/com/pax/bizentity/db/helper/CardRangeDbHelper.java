package com.pax.bizentity.db.helper;

import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.CardRangeDao;
import com.pax.bizentity.entity.CardRange;
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

    @Nullable
    public final CardRange findCardRange(String pan) {
        if (pan.length() < 10){
            return null;
        }
        String subPan = pan.substring(0, 10);
        if (subPan.contains("*")){
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

    public final List<CardRange> findCardRangesOfIssuer(long issuerId) {
        return getNoSessionQuery().where(CardRangeDao.Properties.IssuerId.eq(issuerId)).list();
    }

}
