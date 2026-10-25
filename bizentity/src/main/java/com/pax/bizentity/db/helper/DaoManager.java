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
