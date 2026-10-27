package com.pax.bizentity.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import com.pax.bizentity.entity.CardRange;

public class CardRangeDao extends AbstractDao<CardRange, Long> {

    public static final String TABLENAME = "card_range";

    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "card_id");
        public final static Property Name = new Property(1, String.class, "name", false, "card_name");
        public final static Property IssuerName = new Property(2, String.class, "issuerName", false, "issuer_name");
        public final static Property PanRangeLow = new Property(3, String.class, "panRangeLow", false, "card_range_low");
        public final static Property PanRangeHigh = new Property(4, String.class, "panRangeHigh", false, "card_range_high");
        public final static Property PanLength = new Property(5, int.class, "panLength", false, "card_length");
        public final static Property IssuerId = new Property(6, long.class, "issuerId", false, "ISSUER_ID");
    }

    public CardRangeDao(DaoConfig config) {
        super(config);
    }

    public CardRangeDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"card_range\" (" +
                "\"card_id\" INTEGER PRIMARY KEY AUTOINCREMENT ," +
                "\"card_name\" TEXT," +
                "\"issuer_name\" TEXT," +
                "\"card_range_low\" TEXT NOT NULL UNIQUE ," +
                "\"card_range_high\" TEXT NOT NULL UNIQUE ," +
                "\"card_length\" INTEGER NOT NULL ," +
                "\"ISSUER_ID\" INTEGER NOT NULL );");
    }

    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"card_range\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, CardRange entity) {
        stmt.clearBindings();

        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }

        String name = entity.getName();
        if (name != null) {
            stmt.bindString(2, name);
        }

        String issuerName = entity.getIssuerName();
        if (issuerName != null) {
            stmt.bindString(3, issuerName);
        }
        stmt.bindString(4, entity.getPanRangeLow());
        stmt.bindString(5, entity.getPanRangeHigh());
        stmt.bindLong(6, entity.getPanLength());
        stmt.bindLong(7, entity.getIssuerId());
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, CardRange entity) {
        stmt.clearBindings();

        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }

        String name = entity.getName();
        if (name != null) {
            stmt.bindString(2, name);
        }

        String issuerName = entity.getIssuerName();
        if (issuerName != null) {
            stmt.bindString(3, issuerName);
        }
        stmt.bindString(4, entity.getPanRangeLow());
        stmt.bindString(5, entity.getPanRangeHigh());
        stmt.bindLong(6, entity.getPanLength());
        stmt.bindLong(7, entity.getIssuerId());
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }

    @Override
    public CardRange readEntity(Cursor cursor, int offset) {
        CardRange entity = new CardRange(
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0),
            cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1),
            cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2),
            cursor.getString(offset + 3),
            cursor.getString(offset + 4),
            cursor.getInt(offset + 5),
            cursor.getLong(offset + 6)
        );
        return entity;
    }

    @Override
    public void readEntity(Cursor cursor, CardRange entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setName(cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1));
        entity.setIssuerName(cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2));
        entity.setPanRangeLow(cursor.getString(offset + 3));
        entity.setPanRangeHigh(cursor.getString(offset + 4));
        entity.setPanLength(cursor.getInt(offset + 5));
        entity.setIssuerId(cursor.getLong(offset + 6));
     }

    @Override
    protected final Long updateKeyAfterInsert(CardRange entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    @Override
    public Long getKey(CardRange entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(CardRange entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }

}
