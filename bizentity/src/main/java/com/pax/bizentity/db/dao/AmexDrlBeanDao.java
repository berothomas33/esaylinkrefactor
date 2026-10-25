package com.pax.bizentity.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import com.pax.bizentity.entity.clss.amex.AmexDrlBean;

public class AmexDrlBeanDao extends AbstractDao<AmexDrlBean, Void> {

    public static final String TABLENAME = "amex_drl";

    public static class Properties {
        public final static Property ProgramId = new Property(0, String.class, "programId", false, "PROGRAM_ID");
        public final static Property TransLimit = new Property(1, long.class, "transLimit", false, "TRANS_LIMIT");
        public final static Property TransLimitFlag = new Property(2, byte.class, "transLimitFlag", false, "TRANS_LIMIT_FLAG");
        public final static Property CvmLimit = new Property(3, long.class, "cvmLimit", false, "CVM_LIMIT");
        public final static Property CvmLimitFlag = new Property(4, byte.class, "cvmLimitFlag", false, "CVM_LIMIT_FLAG");
        public final static Property FloorLimit = new Property(5, long.class, "floorLimit", false, "FLOOR_LIMIT");
        public final static Property FloorLimitFlag = new Property(6, byte.class, "floorLimitFlag", false, "FLOOR_LIMIT_FLAG");
        public final static Property StatusCheckFlag = new Property(7, byte.class, "statusCheckFlag", false, "STATUS_CHECK_FLAG");
        public final static Property AmountZeroNoAllowed = new Property(8, byte.class, "amountZeroNoAllowed", false, "AMOUNT_ZERO_NO_ALLOWED");
        public final static Property DynamicLimitSet = new Property(9, byte.class, "dynamicLimitSet", false, "DYNAMIC_LIMIT_SET");
    }

    public AmexDrlBeanDao(DaoConfig config) {
        super(config);
    }

    public AmexDrlBeanDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"amex_drl\" (" +
                "\"PROGRAM_ID\" TEXT," +
                "\"TRANS_LIMIT\" INTEGER NOT NULL ," +
                "\"TRANS_LIMIT_FLAG\" INTEGER NOT NULL ," +
                "\"CVM_LIMIT\" INTEGER NOT NULL ," +
                "\"CVM_LIMIT_FLAG\" INTEGER NOT NULL ," +
                "\"FLOOR_LIMIT\" INTEGER NOT NULL ," +
                "\"FLOOR_LIMIT_FLAG\" INTEGER NOT NULL ," +
                "\"STATUS_CHECK_FLAG\" INTEGER NOT NULL ," +
                "\"AMOUNT_ZERO_NO_ALLOWED\" INTEGER NOT NULL ," +
                "\"DYNAMIC_LIMIT_SET\" INTEGER NOT NULL );");
    }

    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"amex_drl\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, AmexDrlBean entity) {
        stmt.clearBindings();

        String programId = entity.getProgramId();
        if (programId != null) {
            stmt.bindString(1, programId);
        }
        stmt.bindLong(2, entity.getTransLimit());
        stmt.bindLong(3, entity.getTransLimitFlag());
        stmt.bindLong(4, entity.getCvmLimit());
        stmt.bindLong(5, entity.getCvmLimitFlag());
        stmt.bindLong(6, entity.getFloorLimit());
        stmt.bindLong(7, entity.getFloorLimitFlag());
        stmt.bindLong(8, entity.getStatusCheckFlag());
        stmt.bindLong(9, entity.getAmountZeroNoAllowed());
        stmt.bindLong(10, entity.getDynamicLimitSet());
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, AmexDrlBean entity) {
        stmt.clearBindings();

        String programId = entity.getProgramId();
        if (programId != null) {
            stmt.bindString(1, programId);
        }
        stmt.bindLong(2, entity.getTransLimit());
        stmt.bindLong(3, entity.getTransLimitFlag());
        stmt.bindLong(4, entity.getCvmLimit());
        stmt.bindLong(5, entity.getCvmLimitFlag());
        stmt.bindLong(6, entity.getFloorLimit());
        stmt.bindLong(7, entity.getFloorLimitFlag());
        stmt.bindLong(8, entity.getStatusCheckFlag());
        stmt.bindLong(9, entity.getAmountZeroNoAllowed());
        stmt.bindLong(10, entity.getDynamicLimitSet());
    }

    @Override
    public Void readKey(Cursor cursor, int offset) {
        return null;
    }

    @Override
    public AmexDrlBean readEntity(Cursor cursor, int offset) {
        AmexDrlBean entity = new AmexDrlBean(
            cursor.isNull(offset + 0) ? null : cursor.getString(offset + 0),
            cursor.getLong(offset + 1),
            (byte) cursor.getShort(offset + 2),
            cursor.getLong(offset + 3),
            (byte) cursor.getShort(offset + 4),
            cursor.getLong(offset + 5),
            (byte) cursor.getShort(offset + 6),
            (byte) cursor.getShort(offset + 7),
            (byte) cursor.getShort(offset + 8),
            (byte) cursor.getShort(offset + 9)
        );
        return entity;
    }

    @Override
    public void readEntity(Cursor cursor, AmexDrlBean entity, int offset) {
        entity.setProgramId(cursor.isNull(offset + 0) ? null : cursor.getString(offset + 0));
        entity.setTransLimit(cursor.getLong(offset + 1));
        entity.setTransLimitFlag((byte) cursor.getShort(offset + 2));
        entity.setCvmLimit(cursor.getLong(offset + 3));
        entity.setCvmLimitFlag((byte) cursor.getShort(offset + 4));
        entity.setFloorLimit(cursor.getLong(offset + 5));
        entity.setFloorLimitFlag((byte) cursor.getShort(offset + 6));
        entity.setStatusCheckFlag((byte) cursor.getShort(offset + 7));
        entity.setAmountZeroNoAllowed((byte) cursor.getShort(offset + 8));
        entity.setDynamicLimitSet((byte) cursor.getShort(offset + 9));
     }

    @Override
    protected final Void updateKeyAfterInsert(AmexDrlBean entity, long rowId) {
        return null;
    }

    @Override
    public Void getKey(AmexDrlBean entity) {
        return null;
    }

    @Override
    public boolean hasKey(AmexDrlBean entity) {
        return false;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }

}
