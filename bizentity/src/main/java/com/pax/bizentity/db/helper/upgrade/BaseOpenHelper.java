package com.pax.bizentity.db.helper.upgrade;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.pax.bizentity.db.dao.DaoMaster;
import com.pax.commonlib.utils.LogUtils;
import org.greenrobot.greendao.database.Database;

/**
 * Base Open Helper, provide some custom function
 */
public abstract class BaseOpenHelper extends DaoMaster.OpenHelper {
    private static final String TAG = "BaseOpenHelper";

    public BaseOpenHelper(Context context, String name) {
        super(context, name);
    }

    public BaseOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory);
    }

    public void afterDBReady() { }

    /**
     * Migrates the schema keeping the data ({@link SchemaMigrations}) — used by both the plain
     * (debug) and the encrypted (release) helper. Before, both dropped every table on any
     * version change, wiping the EMV parameters, which {@code ConfigInit} doesn't re-seed. A path
     * with no migration (e.g. a downgrade), or a failed one, still recreates the tables.
     */
    @Override
    public void onUpgrade(Database db, int oldVersion, int newVersion) {
        boolean migrated;
        try {
            migrated = SchemaMigrations.migrate(db, oldVersion, newVersion);
        } catch (RuntimeException e) {
            LogUtils.e(TAG, "Migration " + oldVersion + " -> " + newVersion + " failed", e);
            migrated = false;
        }
        if (!migrated) {
            LogUtils.e(TAG, "No migration path from schema " + oldVersion + " to " + newVersion
                    + ", recreating tables");
            DaoMaster.dropAllTables(db, true);
            onCreate(db);
        }
    }
}
