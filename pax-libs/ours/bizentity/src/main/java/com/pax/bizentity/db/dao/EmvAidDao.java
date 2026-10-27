package com.pax.bizentity.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import com.pax.bizentity.entity.EmvAid;

public class EmvAidDao extends AbstractDao<EmvAid, Long> {

    public static final String TABLENAME = "aid";

    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "id");
        public final static Property AppName = new Property(1, String.class, "appName", false, "APP_NAME");
        public final static Property Aid = new Property(2, String.class, "aid", false, "aid");
        public final static Property SelFlag = new Property(3, int.class, "selFlag", false, "SEL_FLAG");
        public final static Property Priority = new Property(4, int.class, "priority", false, "PRIORITY");
        public final static Property OnlinePin = new Property(5, boolean.class, "onlinePin", false, "ONLINE_PIN");
        public final static Property TargetPer = new Property(6, int.class, "targetPer", false, "TARGET_PER");
        public final static Property MaxTargetPer = new Property(7, int.class, "maxTargetPer", false, "MAX_TARGET_PER");
        public final static Property FloorLimitCheckFlg = new Property(8, int.class, "floorLimitCheckFlg", false, "FLOOR_LIMIT_CHECK_FLG");
        public final static Property RandTransSel = new Property(9, boolean.class, "randTransSel", false, "RAND_TRANS_SEL");
        public final static Property VelocityCheck = new Property(10, boolean.class, "velocityCheck", false, "VELOCITY_CHECK");
        public final static Property FloorLimit = new Property(11, long.class, "floorLimit", false, "FLOOR_LIMIT");
        public final static Property Threshold = new Property(12, long.class, "threshold", false, "THRESHOLD");
        public final static Property TacDenial = new Property(13, String.class, "tacDenial", false, "TAC_DENIAL");
        public final static Property TacOnline = new Property(14, String.class, "tacOnline", false, "TAC_ONLINE");
        public final static Property TacDefault = new Property(15, String.class, "tacDefault", false, "TAC_DEFAULT");
        public final static Property AcquirerId = new Property(16, String.class, "acquirerId", false, "ACQUIRER_ID");
        public final static Property DDOL = new Property(17, String.class, "dDOL", false, "D_DOL");
        public final static Property TDOL = new Property(18, String.class, "tDOL", false, "T_DOL");
        public final static Property Version = new Property(19, String.class, "version", false, "VERSION");
        public final static Property RiskManageData = new Property(20, String.class, "riskManageData", false, "RISK_MANAGE_DATA");
        public final static Property TerminalCapability = new Property(21, String.class, "terminalCapability", false, "TERMINAL_CAPABILITY");
        public final static Property TerminalAdditionalCapability = new Property(22, String.class, "terminalAdditionalCapability", false, "TERMINAL_ADDITIONAL_CAPABILITY");
        public final static Property TerminalType = new Property(23, String.class, "terminalType", false, "TERMINAL_TYPE");
        public final static Property GetDataPIN = new Property(24, byte.class, "getDataPIN", false, "GET_DATA_PIN");
        public final static Property BypassPin = new Property(25, byte.class, "bypassPin", false, "BYPASS_PIN");
        public final static Property BypassAllFlg = new Property(26, byte.class, "bypassAllFlg", false, "BYPASS_ALL_FLG");
        public final static Property ForceOnline = new Property(27, byte.class, "forceOnline", false, "FORCE_ONLINE");
    }

    public EmvAidDao(DaoConfig config) {
        super(config);
    }

    public EmvAidDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"aid\" (" +
                "\"id\" INTEGER PRIMARY KEY AUTOINCREMENT ," +
                "\"APP_NAME\" TEXT NOT NULL ," +
                "\"aid\" TEXT NOT NULL UNIQUE ," +
                "\"SEL_FLAG\" INTEGER NOT NULL ," +
                "\"PRIORITY\" INTEGER NOT NULL ," +
                "\"ONLINE_PIN\" INTEGER NOT NULL ," +
                "\"TARGET_PER\" INTEGER NOT NULL ," +
                "\"MAX_TARGET_PER\" INTEGER NOT NULL ," +
                "\"FLOOR_LIMIT_CHECK_FLG\" INTEGER NOT NULL ," +
                "\"RAND_TRANS_SEL\" INTEGER NOT NULL ," +
                "\"VELOCITY_CHECK\" INTEGER NOT NULL ," +
                "\"FLOOR_LIMIT\" INTEGER NOT NULL ," +
                "\"THRESHOLD\" INTEGER NOT NULL ," +
                "\"TAC_DENIAL\" TEXT," +
                "\"TAC_ONLINE\" TEXT," +
                "\"TAC_DEFAULT\" TEXT," +
                "\"ACQUIRER_ID\" TEXT," +
                "\"D_DOL\" TEXT," +
                "\"T_DOL\" TEXT," +
                "\"VERSION\" TEXT," +
                "\"RISK_MANAGE_DATA\" TEXT," +
                "\"TERMINAL_CAPABILITY\" TEXT," +
                "\"TERMINAL_ADDITIONAL_CAPABILITY\" TEXT," +
                "\"TERMINAL_TYPE\" TEXT," +
                "\"GET_DATA_PIN\" INTEGER NOT NULL ," +
                "\"BYPASS_PIN\" INTEGER NOT NULL ," +
                "\"BYPASS_ALL_FLG\" INTEGER NOT NULL ," +
                "\"FORCE_ONLINE\" INTEGER NOT NULL );");
    }

    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"aid\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, EmvAid entity) {
        stmt.clearBindings();

        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
        stmt.bindString(2, entity.getAppName());
        stmt.bindString(3, entity.getAid());
        stmt.bindLong(4, entity.getSelFlag());
        stmt.bindLong(5, entity.getPriority());
        stmt.bindLong(6, entity.getOnlinePin() ? 1L: 0L);
        stmt.bindLong(7, entity.getTargetPer());
        stmt.bindLong(8, entity.getMaxTargetPer());
        stmt.bindLong(9, entity.getFloorLimitCheckFlg());
        stmt.bindLong(10, entity.getRandTransSel() ? 1L: 0L);
        stmt.bindLong(11, entity.getVelocityCheck() ? 1L: 0L);
        stmt.bindLong(12, entity.getFloorLimit());
        stmt.bindLong(13, entity.getThreshold());

        String tacDenial = entity.getTacDenial();
        if (tacDenial != null) {
            stmt.bindString(14, tacDenial);
        }

        String tacOnline = entity.getTacOnline();
        if (tacOnline != null) {
            stmt.bindString(15, tacOnline);
        }

        String tacDefault = entity.getTacDefault();
        if (tacDefault != null) {
            stmt.bindString(16, tacDefault);
        }

        String acquirerId = entity.getAcquirerId();
        if (acquirerId != null) {
            stmt.bindString(17, acquirerId);
        }

        String dDOL = entity.getDDOL();
        if (dDOL != null) {
            stmt.bindString(18, dDOL);
        }

        String tDOL = entity.getTDOL();
        if (tDOL != null) {
            stmt.bindString(19, tDOL);
        }

        String version = entity.getVersion();
        if (version != null) {
            stmt.bindString(20, version);
        }

        String riskManageData = entity.getRiskManageData();
        if (riskManageData != null) {
            stmt.bindString(21, riskManageData);
        }

        String terminalCapability = entity.getTerminalCapability();
        if (terminalCapability != null) {
            stmt.bindString(22, terminalCapability);
        }

        String terminalAdditionalCapability = entity.getTerminalAdditionalCapability();
        if (terminalAdditionalCapability != null) {
            stmt.bindString(23, terminalAdditionalCapability);
        }

        String terminalType = entity.getTerminalType();
        if (terminalType != null) {
            stmt.bindString(24, terminalType);
        }
        stmt.bindLong(25, entity.getGetDataPIN());
        stmt.bindLong(26, entity.getBypassPin());
        stmt.bindLong(27, entity.getBypassAllFlg());
        stmt.bindLong(28, entity.getForceOnline());
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, EmvAid entity) {
        stmt.clearBindings();

        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
        stmt.bindString(2, entity.getAppName());
        stmt.bindString(3, entity.getAid());
        stmt.bindLong(4, entity.getSelFlag());
        stmt.bindLong(5, entity.getPriority());
        stmt.bindLong(6, entity.getOnlinePin() ? 1L: 0L);
        stmt.bindLong(7, entity.getTargetPer());
        stmt.bindLong(8, entity.getMaxTargetPer());
        stmt.bindLong(9, entity.getFloorLimitCheckFlg());
        stmt.bindLong(10, entity.getRandTransSel() ? 1L: 0L);
        stmt.bindLong(11, entity.getVelocityCheck() ? 1L: 0L);
        stmt.bindLong(12, entity.getFloorLimit());
        stmt.bindLong(13, entity.getThreshold());

        String tacDenial = entity.getTacDenial();
        if (tacDenial != null) {
            stmt.bindString(14, tacDenial);
        }

        String tacOnline = entity.getTacOnline();
        if (tacOnline != null) {
            stmt.bindString(15, tacOnline);
        }

        String tacDefault = entity.getTacDefault();
        if (tacDefault != null) {
            stmt.bindString(16, tacDefault);
        }

        String acquirerId = entity.getAcquirerId();
        if (acquirerId != null) {
            stmt.bindString(17, acquirerId);
        }

        String dDOL = entity.getDDOL();
        if (dDOL != null) {
            stmt.bindString(18, dDOL);
        }

        String tDOL = entity.getTDOL();
        if (tDOL != null) {
            stmt.bindString(19, tDOL);
        }

        String version = entity.getVersion();
        if (version != null) {
            stmt.bindString(20, version);
        }

        String riskManageData = entity.getRiskManageData();
        if (riskManageData != null) {
            stmt.bindString(21, riskManageData);
        }

        String terminalCapability = entity.getTerminalCapability();
        if (terminalCapability != null) {
            stmt.bindString(22, terminalCapability);
        }

        String terminalAdditionalCapability = entity.getTerminalAdditionalCapability();
        if (terminalAdditionalCapability != null) {
            stmt.bindString(23, terminalAdditionalCapability);
        }

        String terminalType = entity.getTerminalType();
        if (terminalType != null) {
            stmt.bindString(24, terminalType);
        }
        stmt.bindLong(25, entity.getGetDataPIN());
        stmt.bindLong(26, entity.getBypassPin());
        stmt.bindLong(27, entity.getBypassAllFlg());
        stmt.bindLong(28, entity.getForceOnline());
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }

    @Override
    public EmvAid readEntity(Cursor cursor, int offset) {
        EmvAid entity = new EmvAid(
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0),
            cursor.getString(offset + 1),
            cursor.getString(offset + 2),
            cursor.getInt(offset + 3),
            cursor.getInt(offset + 4),
            cursor.getShort(offset + 5) != 0,
            cursor.getInt(offset + 6),
            cursor.getInt(offset + 7),
            cursor.getInt(offset + 8),
            cursor.getShort(offset + 9) != 0,
            cursor.getShort(offset + 10) != 0,
            cursor.getLong(offset + 11),
            cursor.getLong(offset + 12),
            cursor.isNull(offset + 13) ? null : cursor.getString(offset + 13),
            cursor.isNull(offset + 14) ? null : cursor.getString(offset + 14),
            cursor.isNull(offset + 15) ? null : cursor.getString(offset + 15),
            cursor.isNull(offset + 16) ? null : cursor.getString(offset + 16),
            cursor.isNull(offset + 17) ? null : cursor.getString(offset + 17),
            cursor.isNull(offset + 18) ? null : cursor.getString(offset + 18),
            cursor.isNull(offset + 19) ? null : cursor.getString(offset + 19),
            cursor.isNull(offset + 20) ? null : cursor.getString(offset + 20),
            cursor.isNull(offset + 21) ? null : cursor.getString(offset + 21),
            cursor.isNull(offset + 22) ? null : cursor.getString(offset + 22),
            cursor.isNull(offset + 23) ? null : cursor.getString(offset + 23),
            (byte) cursor.getShort(offset + 24),
            (byte) cursor.getShort(offset + 25),
            (byte) cursor.getShort(offset + 26),
            (byte) cursor.getShort(offset + 27)
        );
        return entity;
    }

    @Override
    public void readEntity(Cursor cursor, EmvAid entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setAppName(cursor.getString(offset + 1));
        entity.setAid(cursor.getString(offset + 2));
        entity.setSelFlag(cursor.getInt(offset + 3));
        entity.setPriority(cursor.getInt(offset + 4));
        entity.setOnlinePin(cursor.getShort(offset + 5) != 0);
        entity.setTargetPer(cursor.getInt(offset + 6));
        entity.setMaxTargetPer(cursor.getInt(offset + 7));
        entity.setFloorLimitCheckFlg(cursor.getInt(offset + 8));
        entity.setRandTransSel(cursor.getShort(offset + 9) != 0);
        entity.setVelocityCheck(cursor.getShort(offset + 10) != 0);
        entity.setFloorLimit(cursor.getLong(offset + 11));
        entity.setThreshold(cursor.getLong(offset + 12));
        entity.setTacDenial(cursor.isNull(offset + 13) ? null : cursor.getString(offset + 13));
        entity.setTacOnline(cursor.isNull(offset + 14) ? null : cursor.getString(offset + 14));
        entity.setTacDefault(cursor.isNull(offset + 15) ? null : cursor.getString(offset + 15));
        entity.setAcquirerId(cursor.isNull(offset + 16) ? null : cursor.getString(offset + 16));
        entity.setDDOL(cursor.isNull(offset + 17) ? null : cursor.getString(offset + 17));
        entity.setTDOL(cursor.isNull(offset + 18) ? null : cursor.getString(offset + 18));
        entity.setVersion(cursor.isNull(offset + 19) ? null : cursor.getString(offset + 19));
        entity.setRiskManageData(cursor.isNull(offset + 20) ? null : cursor.getString(offset + 20));
        entity.setTerminalCapability(cursor.isNull(offset + 21) ? null : cursor.getString(offset + 21));
        entity.setTerminalAdditionalCapability(cursor.isNull(offset + 22) ? null : cursor.getString(offset + 22));
        entity.setTerminalType(cursor.isNull(offset + 23) ? null : cursor.getString(offset + 23));
        entity.setGetDataPIN((byte) cursor.getShort(offset + 24));
        entity.setBypassPin((byte) cursor.getShort(offset + 25));
        entity.setBypassAllFlg((byte) cursor.getShort(offset + 26));
        entity.setForceOnline((byte) cursor.getShort(offset + 27));
     }

    @Override
    protected final Long updateKeyAfterInsert(EmvAid entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    @Override
    public Long getKey(EmvAid entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(EmvAid entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }

}
