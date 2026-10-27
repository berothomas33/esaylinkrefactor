package com.pax.bizentity.db.helper;

import com.pax.bizentity.db.dao.CardBinDao;
import com.pax.bizentity.entity.CardBin;
import java.util.List;
import java.util.Locale;

public class CardBinDbHelper extends BaseDaoHelper<CardBin> {
    private static class LazyHolder {
        public static final CardBinDbHelper INSTANCE = new CardBinDbHelper(CardBin.class);
    }

    public static CardBinDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public CardBinDbHelper(Class<CardBin> entityClass) {
        super(entityClass);
    }

    public final boolean isInCardBinTable(String cardNo) {
        if (cardNo == null || cardNo.isEmpty()) {
            return false;
        }
        List<CardBin> list = getNoSessionQuery().where(CardBinDao.Properties.Bin.like(String.format(
                Locale.US, "%s%%", cardNo))).list();
        return !list.isEmpty();
    }
}
