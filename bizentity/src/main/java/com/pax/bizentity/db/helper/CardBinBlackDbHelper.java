package com.pax.bizentity.db.helper;

import com.pax.bizentity.db.dao.CardBinBlackDao;
import com.pax.bizentity.entity.CardBinBlack;
import java.util.List;

public class CardBinBlackDbHelper extends BaseDaoHelper<CardBinBlack> {
    private static class LazyHolder {
        public static final CardBinBlackDbHelper INSTANCE = new CardBinBlackDbHelper(CardBinBlack.class);
    }

    public static CardBinBlackDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public CardBinBlackDbHelper(Class<CardBinBlack> entityClass) {
        super(entityClass);
    }

    public final boolean isBlack(String cardNo) {
        if (cardNo == null || cardNo.isEmpty()) {
            return false;
        }
        List<CardBinBlack> list = getNoSessionQuery().where(CardBinBlackDao.Properties.Bin.like(String.format(
                "%s%%", cardNo))).list();
        return !list.isEmpty();

    }
}
