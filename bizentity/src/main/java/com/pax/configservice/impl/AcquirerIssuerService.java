package com.pax.configservice.impl;

import com.pax.bizentity.db.helper.GreendaoHelper;
import com.pax.bizentity.entity.Acquirer;
import com.pax.bizentity.entity.CardRange;
import com.pax.bizentity.entity.Issuer;
import java.util.List;
public class AcquirerIssuerService {
    public boolean insertAcquirer(List<Acquirer> acquirerList) {
        return GreendaoHelper.getAcquirerHelper().insert(acquirerList);
    }

    public Acquirer findAcquirer(String acquirerName) {
        return GreendaoHelper.getAcquirerHelper().findAcquirer(acquirerName);
    }

    public List<Acquirer> findAllAcquirers() {
        return GreendaoHelper.getAcquirerHelper().loadAll();
    }

    public void insertIssuer(List<Issuer> issuerList) {
        GreendaoHelper.getIssuerHelper().insert(issuerList);
    }

    public boolean insertCardRange(List<CardRange> cardRangeList) {
        return GreendaoHelper.getCardRangeHelper().insert(cardRangeList);
    }

    public CardRange findCardRange(String pan) {
        return GreendaoHelper.getCardRangeHelper().findCardRange(pan);
    }
}
