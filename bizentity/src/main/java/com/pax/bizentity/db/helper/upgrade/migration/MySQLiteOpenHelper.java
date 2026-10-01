package com.pax.bizentity.db.helper.upgrade.migration;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.pax.bizentity.db.helper.upgrade.BaseOpenHelper;

public class MySQLiteOpenHelper extends BaseOpenHelper {
    public MySQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory);
    }
}
