package com.pax.bizentity.db.helper;

public class GreendaoHelper {

    private GreendaoHelper() {
    }

    public static AcquirerDbHelper getAcquirerHelper() {
        return AcquirerDbHelper.getInstance();
    }

    public static IssuerDbHelper getIssuerHelper() {
        return IssuerDbHelper.getInstance();
    }

    public static CardBinDbHelper getCardBinHelper() {
        return CardBinDbHelper.getInstance();
    }

    public static CardBinBlackDbHelper getCardBinBlackHelper() {
        return CardBinBlackDbHelper.getInstance();
    }

    public static CardRangeDbHelper getCardRangeHelper() {
        return CardRangeDbHelper.getInstance();
    }

    public static EmvAidDbHelper getEmvAidHelper() {
        return EmvAidDbHelper.getInstance();
    }

    public static EmvCapkDbHelper getEmvCapkHelper() {
        return EmvCapkDbHelper.getInstance();
    }

    public static TransDataDbHelper getTransDataHelper() {
        return TransDataDbHelper.getInstance();
    }

    public static TransTotalDbHelper getTransTotalHelper() {
        return TransTotalDbHelper.getInstance();
    }
}
