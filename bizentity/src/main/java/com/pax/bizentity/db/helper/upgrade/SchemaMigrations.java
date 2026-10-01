package com.pax.bizentity.db.helper.upgrade;

import org.greenrobot.greendao.database.Database;

/**
 * Step-by-step GreenDAO schema migrations that keep the data. Each step upgrades one version and
 * only adds what that version introduced; see {@link BaseOpenHelper#onUpgrade}.
 */
public final class SchemaMigrations {

    private SchemaMigrations() {
    }

    /**
     * Upgrades {@code db} from {@code oldVersion} to {@code newVersion}.
     *
     * @return {@code false} when there's no migration path (e.g. a downgrade) — the caller then
     *     recreates the tables
     */
    public static boolean migrate(Database db, int oldVersion, int newVersion) {
        if (oldVersion >= newVersion) {
            return false;
        }
        if (oldVersion < 2) {
            // 1 → 2: PayPass Mobile Support Indicator (9F7E) and Account Type (5F57), from the
            // host XML's PAYPASSCONFIGURATION. Existing rows get NULL until the next EMV
            // parameter download fills them.
            addColumn(db, "paypass_aid", "MOBILE_SUPPORT", "TEXT");
            addColumn(db, "paypass_aid", "ACCOUNT_TYPE", "TEXT");
        }
        return true;
    }

    private static void addColumn(Database db, String table, String column, String type) {
        db.execSQL("ALTER TABLE \"" + table + "\" ADD COLUMN \"" + column + "\" " + type);
    }
}
