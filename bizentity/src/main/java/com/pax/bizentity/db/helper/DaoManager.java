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

import com.pax.bizentity.BuildConfig;
import com.pax.bizentity.db.dao.DaoMaster;
import com.pax.bizentity.db.dao.DaoSession;
import com.pax.bizentity.db.helper.upgrade.BaseOpenHelper;
import com.pax.bizentity.db.helper.upgrade.MyEncryptedSQLiteOpenHelper;
import com.pax.bizentity.db.helper.upgrade.migration.MySQLiteOpenHelper;
import com.pax.commonlib.application.BaseApplication;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.query.QueryBuilder;

/**
 * GreenDao Database Manager
 */
public class DaoManager {
    private BaseOpenHelper openHelper;
    private Database database;
    private DaoSession daoSession;
    private DaoManager() {
    }

    private static class LazyHolder {
        public static final DaoManager INSTANCE = new DaoManager();
    }

    public static DaoManager getInstance() {
        return LazyHolder.INSTANCE;
    }

    /** No-op if already initialized — safe to call more than once. */
    public void init(){
        if (daoSession != null) {
            return;
        }
        if (BuildConfig.RELEASE) {
            openHelper = new MyEncryptedSQLiteOpenHelper(BaseApplication.getAppContext(), "data.db", null);
            database = openHelper.getEncryptedWritableDb(BuildConfig.DATABASE_PWD);
        } else {
            openHelper = new MySQLiteOpenHelper(BaseApplication.getAppContext(), "data.db", null);
            database = openHelper.getWritableDb();
        }
        DaoMaster daoMaster = new DaoMaster(database);
        daoSession = daoMaster.newSession();
        QueryBuilder.LOG_SQL = !BuildConfig.RELEASE;
        QueryBuilder.LOG_VALUES = !BuildConfig.RELEASE;
        openHelper.afterDBReady();
    }


    public DaoSession getDaoSession() {
        return daoSession;
    }

    public DaoMaster.OpenHelper getOpenHelper() {
        return openHelper;
    }

    public Database getDatabase() {
        return database;
    }

}
