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

/**
 * greendao database helper
 */
public class GreendaoHelper {
    private static AcquirerDbHelper acquirerHelper;
    private static IssuerDbHelper issuerHelper;
    private static AcqIssuerRelationDbHelper acqIssuerRelationHelper;
    private static CardBinDbHelper cardBinHelper;
    private static CardBinBlackDbHelper cardBinBlackHelper;
    private static CardRangeDbHelper cardRangeHelper;
    private static EmvAidDbHelper emvAidHelper;
    private static EmvCapkDbHelper emvCapkHelper;
    private static TransDataDbHelper transDataHelper;
    private static TransTotalDbHelper transTotalHelper;

    private GreendaoHelper() {
        // do nothing
    }

    /**
     * get acquirer database helper
     * @return acquirer database helper
     */
    public static AcquirerDbHelper getAcquirerHelper() {
        if (acquirerHelper == null) {
            acquirerHelper = AcquirerDbHelper.getInstance();
        }
        return acquirerHelper;
    }

    /**
     * get Issuer database helper
     * @return Issuer database helper
     */
    public static IssuerDbHelper getIssuerHelper() {
        if (issuerHelper == null) {
            issuerHelper = IssuerDbHelper.getInstance();
        }
        return issuerHelper;
    }

    /**
     * get AcqIssuerRelation database helper
     * @return AcqIssuerRelation database helper
     */
    public static AcqIssuerRelationDbHelper getAcqIssuerRelationHelper() {
        if (acqIssuerRelationHelper == null) {
            acqIssuerRelationHelper = AcqIssuerRelationDbHelper.getInstance();
        }
        return acqIssuerRelationHelper;
    }

    /**
     * get CardBin database helper
     * @return CardBin database helper
     */
    public static CardBinDbHelper getCardBinHelper() {
        if (cardBinHelper == null) {
            cardBinHelper = CardBinDbHelper.getInstance();
        }
        return cardBinHelper;
    }

    /**
     * get CardBinBlack database helper
     * @return CardBinBlack database helper
     */
    public static CardBinBlackDbHelper getCardBinBlackHelper() {
        if (cardBinBlackHelper == null) {
            cardBinBlackHelper = CardBinBlackDbHelper.getInstance();
        }
        return cardBinBlackHelper;
    }

    /**
     * get CardRange database helper
     * @return CardRange database helper
     */
    public static CardRangeDbHelper getCardRangeHelper() {
        if (cardRangeHelper == null) {
            cardRangeHelper = CardRangeDbHelper.getInstance();
        }
        return cardRangeHelper;
    }

    /**
     * get EmvAid database helper
     * @return EmvAid database helper
     */
    public static EmvAidDbHelper getEmvAidHelper() {
        if (emvAidHelper == null) {
            emvAidHelper = EmvAidDbHelper.getInstance();
        }
        return emvAidHelper;
    }

    /**
     * get EmvCapk database helper
     * @return EmvCapk database helper
     */
    public static EmvCapkDbHelper getEmvCapkHelper() {
        if (emvCapkHelper == null) {
            emvCapkHelper = EmvCapkDbHelper.getInstance();
        }
        return emvCapkHelper;
    }

    /**
     * get TransData database helper
     * @return TransData database helper
     */
    public static TransDataDbHelper getTransDataHelper() {
        if (transDataHelper == null) {
            transDataHelper = TransDataDbHelper.getInstance();
        }
        return transDataHelper;
    }

    /**
     * get TransTotal database helper
     * @return TransTotal database helper
     */
    public static TransTotalDbHelper getTransTotalHelper() {
        if (transTotalHelper == null) {
            transTotalHelper = TransTotalDbHelper.getInstance();
        }
        return transTotalHelper;
    }
}
