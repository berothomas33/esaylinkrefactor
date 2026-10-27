package com.pax.bizentity.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import com.pax.bizentity.entity.clss.jcb.JcbAidBean;

public class JcbAidBeanDao extends AbstractDao<JcbAidBean, Long> {

    public static final String TABLENAME = "jcb_aid";

    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "jcb_id");
        public final static Property AppName = new Property(1, String.class, "appName", false, "APP_NAME");
        public final static Property Aid = new Property(2, String.class, "aid", false, "AID");
        public final static Property SelFlag = new Property(3, byte.class, "selFlag", false, "SEL_FLAG");
        public final static Property TransLimit = new Property(4, long.class, "transLimit", false, "TRANS_LIMIT");
        public final static Property TransLimitFlag = new Property(5, byte.class, "transLimitFlag", false, "TRANS_LIMIT_FLAG");
        public final static Property CvmTransLimit = new Property(6, long.class, "cvmTransLimit", false, "CVM_TRANS_LIMIT");
        public final static Property FloorLimit = new Property(7, long.class, "floorLimit", false, "FLOOR_LIMIT");
        public final static Property FloorLimitFlag = new Property(8, byte.class, "floorLimitFlag", false, "FLOOR_LIMIT_FLAG");
        public final static Property CvmLimit = new Property(9, long.class, "cvmLimit", false, "CVM_LIMIT");
        public final static Property CvmLimitFlag = new Property(10, byte.class, "cvmLimitFlag", false, "CVM_LIMIT_FLAG");
        public final static Property TacDenial = new Property(11, String.class, "tacDenial", false, "TAC_DENIAL");
        public final static Property TacOnline = new Property(12, String.class, "tacOnline", false, "TAC_ONLINE");
        public final static Property TacDefault = new Property(13, String.class, "tacDefault", false, "TAC_DEFAULT");
        public final static Property AcquirerId = new Property(14, String.class, "acquirerId", false, "ACQUIRER_ID");
        public final static Property Version = new Property(15, String.class, "version", false, "VERSION");
        public final static Property TerminalType = new Property(16, String.class, "terminalType", false, "TERMINAL_TYPE");
        public final static Property TermInterchange = new Property(17, String.class, "termInterchange", false, "TERM_INTERCHANGE");
        public final static Property TermCompatFlag = new Property(18, byte.class, "termCompatFlag", false, "TERM_COMPAT_FLAG");
        public final static Property CombinationOption = new Property(19, String.class, "combinationOption", false, "COMBINATION_OPTION");
    }

    public JcbAidBeanDao(DaoConfig config) {
        super(config);
    }

    public JcbAidBeanDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"jcb_aid\" (" +
                "\"jcb_id\" INTEGER PRIMARY KEY AUTOINCREMENT ," +
                "\"APP_NAME\" TEXT," +
                "\"AID\" TEXT," +
                "\"SEL_FLAG\" INTEGER NOT NULL ," +
                "\"TRANS_LIMIT\" INTEGER NOT NULL ," +
                "\"TRANS_LIMIT_FLAG\" INTEGER NOT NULL ," +
                "\"CVM_TRANS_LIMIT\" INTEGER NOT NULL ," +
                "\"FLOOR_LIMIT\" INTEGER NOT NULL ," +
                "\"FLOOR_LIMIT_FLAG\" INTEGER NOT NULL ," +
                "\"CVM_LIMIT\" INTEGER NOT NULL ," +
                "\"CVM_LIMIT_FLAG\" INTEGER NOT NULL ," +
                "\"TAC_DENIAL\" TEXT," +
                "\"TAC_ONLINE\" TEXT," +
                "\"TAC_DEFAULT\" TEXT," +
                "\"ACQUIRER_ID\" TEXT," +
                "\"VERSION\" TEXT," +
                "\"TERMINAL_TYPE\" TEXT," +
                "\"TERM_INTERCHANGE\" TEXT," +
                "\"TERM_COMPAT_FLAG\" INTEGER NOT NULL ," +
                "\"COMBINATION_OPTION\" TEXT);");
    }

    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"jcb_aid\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, JcbAidBean entity) {
        stmt.clearBindings();

        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }

        String appName = entity.getAppName();
        if (appName != null) {
            stmt.bindString(2, appName);
        }

        String aid = entity.getAid();
        if (aid != null) {
            stmt.bindString(3, aid);
        }
        stmt.bindLong(4, entity.getSelFlag());
        stmt.bindLong(5, entity.getTransLimit());
        stmt.bindLong(6, entity.getTransLimitFlag());
        stmt.bindLong(7, entity.getCvmTransLimit());
        stmt.bindLong(8, entity.getFloorLimit());
        stmt.bindLong(9, entity.getFloorLimitFlag());
        stmt.bindLong(10, entity.getCvmLimit());
        stmt.bindLong(11, entity.getCvmLimitFlag());

        String tacDenial = entity.getTacDenial();
        if (tacDenial != null) {
            stmt.bindString(12, tacDenial);
        }

        String tacOnline = entity.getTacOnline();
        if (tacOnline != null) {
            stmt.bindString(13, tacOnline);
        }

        String tacDefault = entity.getTacDefault();
        if (tacDefault != null) {
            stmt.bindString(14, tacDefault);
        }

        String acquirerId = entity.getAcquirerId();
        if (acquirerId != null) {
            stmt.bindString(15, acquirerId);
        }

        String version = entity.getVersion();
        if (version != null) {
            stmt.bindString(16, version);
        }

        String terminalType = entity.getTerminalType();
        if (terminalType != null) {
            stmt.bindString(17, terminalType);
        }

        String termInterchange = entity.getTermInterchange();
        if (termInterchange != null) {
            stmt.bindString(18, termInterchange);
        }
        stmt.bindLong(19, entity.getTermCompatFlag());

        String combinationOption = entity.getCombinationOption();
        if (combinationOption != null) {
            stmt.bindString(20, combinationOption);
        }
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, JcbAidBean entity) {
        stmt.clearBindings();

        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }

        String appName = entity.getAppName();
        if (appName != null) {
            stmt.bindString(2, appName);
        }

        String aid = entity.getAid();
        if (aid != null) {
            stmt.bindString(3, aid);
        }
        stmt.bindLong(4, entity.getSelFlag());
        stmt.bindLong(5, entity.getTransLimit());
        stmt.bindLong(6, entity.getTransLimitFlag());
        stmt.bindLong(7, entity.getCvmTransLimit());
        stmt.bindLong(8, entity.getFloorLimit());
        stmt.bindLong(9, entity.getFloorLimitFlag());
        stmt.bindLong(10, entity.getCvmLimit());
        stmt.bindLong(11, entity.getCvmLimitFlag());

        String tacDenial = entity.getTacDenial();
        if (tacDenial != null) {
            stmt.bindString(12, tacDenial);
        }

        String tacOnline = entity.getTacOnline();
        if (tacOnline != null) {
            stmt.bindString(13, tacOnline);
        }

        String tacDefault = entity.getTacDefault();
        if (tacDefault != null) {
            stmt.bindString(14, tacDefault);
        }

        String acquirerId = entity.getAcquirerId();
        if (acquirerId != null) {
            stmt.bindString(15, acquirerId);
        }

        String version = entity.getVersion();
        if (version != null) {
            stmt.bindString(16, version);
        }

        String terminalType = entity.getTerminalType();
        if (terminalType != null) {
            stmt.bindString(17, terminalType);
        }

        String termInterchange = entity.getTermInterchange();
        if (termInterchange != null) {
            stmt.bindString(18, termInterchange);
        }
        stmt.bindLong(19, entity.getTermCompatFlag());

        String combinationOption = entity.getCombinationOption();
        if (combinationOption != null) {
            stmt.bindString(20, combinationOption);
        }
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }

    @Override
    public JcbAidBean readEntity(Cursor cursor, int offset) {
        JcbAidBean entity = new JcbAidBean(
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0),
            cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1),
            cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2),
            (byte) cursor.getShort(offset + 3),
            cursor.getLong(offset + 4),
            (byte) cursor.getShort(offset + 5),
            cursor.getLong(offset + 6),
            cursor.getLong(offset + 7),
            (byte) cursor.getShort(offset + 8),
            cursor.getLong(offset + 9),
            (byte) cursor.getShort(offset + 10),
            cursor.isNull(offset + 11) ? null : cursor.getString(offset + 11),
            cursor.isNull(offset + 12) ? null : cursor.getString(offset + 12),
            cursor.isNull(offset + 13) ? null : cursor.getString(offset + 13),
            cursor.isNull(offset + 14) ? null : cursor.getString(offset + 14),
            cursor.isNull(offset + 15) ? null : cursor.getString(offset + 15),
            cursor.isNull(offset + 16) ? null : cursor.getString(offset + 16),
            cursor.isNull(offset + 17) ? null : cursor.getString(offset + 17),
            (byte) cursor.getShort(offset + 18),
            cursor.isNull(offset + 19) ? null : cursor.getString(offset + 19)
        );
        return entity;
    }

    @Override
    public void readEntity(Cursor cursor, JcbAidBean entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setAppName(cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1));
        entity.setAid(cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2));
        entity.setSelFlag((byte) cursor.getShort(offset + 3));
        entity.setTransLimit(cursor.getLong(offset + 4));
        entity.setTransLimitFlag((byte) cursor.getShort(offset + 5));
        entity.setCvmTransLimit(cursor.getLong(offset + 6));
        entity.setFloorLimit(cursor.getLong(offset + 7));
        entity.setFloorLimitFlag((byte) cursor.getShort(offset + 8));
        entity.setCvmLimit(cursor.getLong(offset + 9));
        entity.setCvmLimitFlag((byte) cursor.getShort(offset + 10));
        entity.setTacDenial(cursor.isNull(offset + 11) ? null : cursor.getString(offset + 11));
        entity.setTacOnline(cursor.isNull(offset + 12) ? null : cursor.getString(offset + 12));
        entity.setTacDefault(cursor.isNull(offset + 13) ? null : cursor.getString(offset + 13));
        entity.setAcquirerId(cursor.isNull(offset + 14) ? null : cursor.getString(offset + 14));
        entity.setVersion(cursor.isNull(offset + 15) ? null : cursor.getString(offset + 15));
        entity.setTerminalType(cursor.isNull(offset + 16) ? null : cursor.getString(offset + 16));
        entity.setTermInterchange(cursor.isNull(offset + 17) ? null : cursor.getString(offset + 17));
        entity.setTermCompatFlag((byte) cursor.getShort(offset + 18));
        entity.setCombinationOption(cursor.isNull(offset + 19) ? null : cursor.getString(offset + 19));
     }

    @Override
    protected final Long updateKeyAfterInsert(JcbAidBean entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    @Override
    public Long getKey(JcbAidBean entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(JcbAidBean entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }

}
