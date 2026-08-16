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
import com.pax.bizentity.db.helper.upgrade.history.Upgrade1To2;
import com.pax.bizentity.db.helper.upgrade.history.Upgrade4To5;
import com.pax.bizentity.db.helper.upgrade.history.Upgrade5To6;
import com.pax.bizentity.db.helper.upgrade.history.Upgrade6To7;
import com.pax.bizentity.db.helper.upgrade.migration.MigrationHelper;
import com.pax.commonlib.utils.LogUtils;
import java.util.LinkedList;
import java.util.List;
import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.database.Database;

public abstract class DbUpgrade {
    protected static final String TAG = "DbUpgrade";
    private static final List<AfterDBReadyCallback> afterDBReadyCallbackList = new LinkedList<>();

    public static void upgrade(Database db, int oldVersion, int newVersion){
        try {
            DbUpgrade upgrade = createUpgrade(UpgradeConst.getKey(oldVersion, newVersion));
            upgrade.upgrade(db);
        } catch (Exception e) {
            LogUtils.e(TAG,e);
        }
    }

    /** when a new schema version ships, add its upgrade class here */
    private static DbUpgrade createUpgrade(String key) {
        switch (key) {
            case UpgradeConst.UPGRADE_1_2:
                return new Upgrade1To2();
            case UpgradeConst.UPGRADE_4_5:
                return new Upgrade4To5();
            case UpgradeConst.UPGRADE_5_6:
                return new Upgrade5To6();
            case UpgradeConst.UPGRADE_6_7:
                return new Upgrade6To7();
            default:
                throw new IllegalArgumentException("No DbUpgrade registered for " + key);
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
