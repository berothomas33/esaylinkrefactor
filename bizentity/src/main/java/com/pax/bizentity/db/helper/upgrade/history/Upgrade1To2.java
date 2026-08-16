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
 * Date                  Author	                 Action
 * 20190621  	         xieYb                   Create
 * ===========================================================================================
 */
package com.pax.bizentity.db.helper.upgrade.history;

import android.database.SQLException;
import com.pax.bizentity.db.dao.AcqIssuerRelationDao;
import com.pax.bizentity.db.dao.AcquirerDao;
import com.pax.bizentity.db.dao.CardBinDao;
import com.pax.bizentity.db.dao.CardRangeDao;
import com.pax.bizentity.db.dao.ClssTornLogDao;
import com.pax.bizentity.db.dao.EmvAidDao;
import com.pax.bizentity.db.dao.EmvCapkDao;
import com.pax.bizentity.db.dao.IssuerDao;
import com.pax.bizentity.db.dao.TransDataDao;
import com.pax.bizentity.db.dao.TransTotalDao;
import com.pax.bizentity.db.helper.upgrade.DbUpgrade;
import com.pax.commonlib.utils.LogUtils;
import org.greenrobot.greendao.database.Database;
/**
 * update database from version 1 to 2
 */
public class Upgrade1To2 extends DbUpgrade {

    @Override
    protected void upgrade(Database db)  {
        try {
            //upgrade all database table
            DbUpgrade.upgradeTable(db, AcquirerDao.class, IssuerDao.class, AcqIssuerRelationDao.class, CardBinDao.class, CardRangeDao.class, ClssTornLogDao.class, EmvAidDao.class, EmvCapkDao.class, TransDataDao.class, TransTotalDao.class);
        } catch (SQLException e) {
            LogUtils.e(TAG,e);
        }
    }
}
