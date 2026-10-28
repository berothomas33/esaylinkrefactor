package com.pax.bizentity.db.helper.upgrade;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.pax.bizentity.db.dao.DaoMaster;

public abstract class BaseOpenHelper extends DaoMaster.OpenHelper {
    public BaseOpenHelper(Context context, String name) {
        super(context, name);
    }

    public BaseOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory);
    }

    public void afterDBReady() { }
}
