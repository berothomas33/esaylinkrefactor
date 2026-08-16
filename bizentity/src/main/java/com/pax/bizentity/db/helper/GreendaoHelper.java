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
 * Greendao database helper lookup. Every {@code *DbHelper} already caches itself via its own
 * {@code LazyHolder}, so these are plain delegates — no caching here to duplicate.
 */
public class GreendaoHelper {

    private GreendaoHelper() {
        // do nothing
    }

    public static AcquirerDbHelper getAcquirerHelper() {
        return AcquirerDbHelper.getInstance();
    }

    public static IssuerDbHelper getIssuerHelper() {
        return IssuerDbHelper.getInstance();
    }

    public static AcqIssuerRelationDbHelper getAcqIssuerRelationHelper() {
        return AcqIssuerRelationDbHelper.getInstance();
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
