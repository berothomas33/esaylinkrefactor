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
package com.pax.bizentity.db.helper.upgrade;

import android.database.SQLException;
import com.pax.bizentity.db.helper.upgrade.migration.MigrationHelper;
import com.pax.commonlib.utils.LogUtils;
import com.sankuai.waimai.router.Router;
import java.util.LinkedList;
import java.util.List;
import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.database.Database;

public abstract class DbUpgrade {
    protected static final String TAG = "DbUpgrade";
    private static final List<AfterDBReadyCallback> afterDBReadyCallbackList = new LinkedList<>();

    public static void upgrade(Database db, int oldVersion, int newVersion){
        try {
            DbUpgrade upgrade = Router.getService(DbUpgrade.class, UpgradeConst.getKey(oldVersion, newVersion));
            upgrade.upgrade(db);
        } catch (Exception e) {
            LogUtils.e(TAG,e);
        }
    }

    protected abstract void upgrade(Database db);

    protected void addAfterDBReadyCallback(AfterDBReadyCallback callback) {
        afterDBReadyCallbackList.add(callback);
    }

    public static void executeAfterDBReadyCallback() {
        if (!afterDBReadyCallbackList.isEmpty()) {
            for (AfterDBReadyCallback callback : afterDBReadyCallbackList) {
                callback.onReady();
            }
        }
    }

    @SafeVarargs
    protected static void upgradeTable(Database db, Class<? extends AbstractDao<?, ?>>... daoClasses) throws SQLException {
        MigrationHelper.migrate(db,daoClasses);
    }

    public interface AfterDBReadyCallback {
        void onReady();
    }
}
