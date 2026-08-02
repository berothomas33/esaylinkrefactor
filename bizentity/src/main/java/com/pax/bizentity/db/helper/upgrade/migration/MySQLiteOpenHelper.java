package com.pax.bizentity.db.helper.upgrade.migration;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.pax.bizentity.db.helper.upgrade.BaseOpenHelper;
import com.pax.bizentity.db.helper.upgrade.DbUpgrade;
import org.greenrobot.greendao.database.Database;

public class MySQLiteOpenHelper extends BaseOpenHelper {
    public MySQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory);
    }
    @Override
    public void onUpgrade(Database db, int oldVersion, int newVersion) {
        for (int i = oldVersion;i<newVersion;i++){
            DbUpgrade.upgrade(db,i,i+1);
        }
    }

    @Override
    public void afterDBReady() {
        DbUpgrade.executeAfterDBReadyCallback();
    }
}
