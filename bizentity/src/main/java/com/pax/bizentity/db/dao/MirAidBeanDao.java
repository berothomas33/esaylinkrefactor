package com.pax.bizentity.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import com.pax.bizentity.entity.clss.mir.MirAidBean;

public class MirAidBeanDao extends AbstractDao<MirAidBean, Long> {

    public static final String TABLENAME = "mir_aid";

    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "mir_id");
        public final static Property AppName = new Property(1, String.class, "appName", false, "APP_NAME");
        public final static Property Aid = new Property(2, String.class, "aid", false, "AID");
        public final static Property SelFlag = new Property(3, byte.class, "selFlag", false, "SEL_FLAG");
        public final static Property TransLimit = new Property(4, long.class, "transLimit", false, "TRANS_LIMIT");
        public final static Property TransLimitFlag = new Property(5, byte.class, "transLimitFlag", false, "TRANS_LIMIT_FLAG");
        public final static Property FloorLimit = new Property(6, long.class, "floorLimit", false, "FLOOR_LIMIT");
        public final static Property FloorLimitFlag = new Property(7, byte.class, "floorLimitFlag", false, "FLOOR_LIMIT_FLAG");
        public final static Property CvmLimit = new Property(8, long.class, "cvmLimit", false, "CVM_LIMIT");
        public final static Property CvmLimitFlag = new Property(9, byte.class, "cvmLimitFlag", false, "CVM_LIMIT_FLAG");
        public final static Property TacDenial = new Property(10, String.class, "tacDenial", false, "TAC_DENIAL");
        public final static Property TacOnline = new Property(11, String.class, "tacOnline", false, "TAC_ONLINE");
        public final static Property TacDefault = new Property(12, String.class, "tacDefault", false, "TAC_DEFAULT");
        public final static Property AcquirerId = new Property(13, String.class, "acquirerId", false, "ACQUIRER_ID");
        public final static Property Version = new Property(14, String.class, "version", false, "VERSION");
        public final static Property TerminalType = new Property(15, String.class, "terminalType", false, "TERMINAL_TYPE");
        public final static Property RefundFloorLimit = new Property(16, long.class, "refundFloorLimit", false, "REFUND_FLOOR_LIMIT");
        public final static Property RefundTacDenial = new Property(17, String.class, "refundTacDenial", false, "REFUND_TAC_DENIAL");
        public final static Property TermTPMCapability = new Property(18, String.class, "termTPMCapability", false, "TERM_TPMCAPABILITY");
        public final static Property TlvDF810CTag = new Property(19, String.class, "tlvDF810CTag", false, "TLV_DF810_CTAG");
        public final static Property TransRecoveryLimit = new Property(20, String.class, "transRecoveryLimit", false, "TRANS_RECOVERY_LIMIT");
        public final static Property DataExchangeTagList = new Property(21, String.class, "dataExchangeTagList", false, "DATA_EXCHANGE_TAG_LIST");
        public final static Property Protocol2Tag82 = new Property(22, String.class, "protocol2Tag82", false, "PROTOCOL2_TAG82");
        public final static Property ExceptFileFlag = new Property(23, byte.class, "exceptFileFlag", false, "EXCEPT_FILE_FLAG");
    }

    public MirAidBeanDao(DaoConfig config) {
        super(config);
    }

    public MirAidBeanDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"mir_aid\" (" +
                "\"mir_id\" INTEGER PRIMARY KEY AUTOINCREMENT ," +
                "\"APP_NAME\" TEXT," +
                "\"AID\" TEXT," +
                "\"SEL_FLAG\" INTEGER NOT NULL ," +
                "\"TRANS_LIMIT\" INTEGER NOT NULL ," +
                "\"TRANS_LIMIT_FLAG\" INTEGER NOT NULL ," +
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
                "\"REFUND_FLOOR_LIMIT\" INTEGER NOT NULL ," +
                "\"REFUND_TAC_DENIAL\" TEXT," +
                "\"TERM_TPMCAPABILITY\" TEXT," +
                "\"TLV_DF810_CTAG\" TEXT," +
                "\"TRANS_RECOVERY_LIMIT\" TEXT," +
                "\"DATA_EXCHANGE_TAG_LIST\" TEXT," +
                "\"PROTOCOL2_TAG82\" TEXT," +
                "\"EXCEPT_FILE_FLAG\" INTEGER NOT NULL );");
    }

    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"mir_aid\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, MirAidBean entity) {
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
        stmt.bindLong(7, entity.getFloorLimit());
        stmt.bindLong(8, entity.getFloorLimitFlag());
        stmt.bindLong(9, entity.getCvmLimit());
        stmt.bindLong(10, entity.getCvmLimitFlag());

        String tacDenial = entity.getTacDenial();
        if (tacDenial != null) {
            stmt.bindString(11, tacDenial);
        }

        String tacOnline = entity.getTacOnline();
        if (tacOnline != null) {
            stmt.bindString(12, tacOnline);
        }

        String tacDefault = entity.getTacDefault();
        if (tacDefault != null) {
            stmt.bindString(13, tacDefault);
        }

        String acquirerId = entity.getAcquirerId();
        if (acquirerId != null) {
            stmt.bindString(14, acquirerId);
        }

        String version = entity.getVersion();
        if (version != null) {
            stmt.bindString(15, version);
        }

        String terminalType = entity.getTerminalType();
        if (terminalType != null) {
            stmt.bindString(16, terminalType);
        }
        stmt.bindLong(17, entity.getRefundFloorLimit());

        String refundTacDenial = entity.getRefundTacDenial();
        if (refundTacDenial != null) {
            stmt.bindString(18, refundTacDenial);
        }

        String termTPMCapability = entity.getTermTPMCapability();
        if (termTPMCapability != null) {
            stmt.bindString(19, termTPMCapability);
        }

        String tlvDF810CTag = entity.getTlvDF810CTag();
        if (tlvDF810CTag != null) {
            stmt.bindString(20, tlvDF810CTag);
        }

        String transRecoveryLimit = entity.getTransRecoveryLimit();
        if (transRecoveryLimit != null) {
            stmt.bindString(21, transRecoveryLimit);
        }

        String dataExchangeTagList = entity.getDataExchangeTagList();
        if (dataExchangeTagList != null) {
            stmt.bindString(22, dataExchangeTagList);
        }

        String protocol2Tag82 = entity.getProtocol2Tag82();
        if (protocol2Tag82 != null) {
            stmt.bindString(23, protocol2Tag82);
        }
        stmt.bindLong(24, entity.getExceptFileFlag());
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, MirAidBean entity) {
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
        stmt.bindLong(7, entity.getFloorLimit());
        stmt.bindLong(8, entity.getFloorLimitFlag());
        stmt.bindLong(9, entity.getCvmLimit());
        stmt.bindLong(10, entity.getCvmLimitFlag());

        String tacDenial = entity.getTacDenial();
        if (tacDenial != null) {
            stmt.bindString(11, tacDenial);
        }

        String tacOnline = entity.getTacOnline();
        if (tacOnline != null) {
            stmt.bindString(12, tacOnline);
        }

        String tacDefault = entity.getTacDefault();
        if (tacDefault != null) {
            stmt.bindString(13, tacDefault);
        }

        String acquirerId = entity.getAcquirerId();
        if (acquirerId != null) {
            stmt.bindString(14, acquirerId);
        }

        String version = entity.getVersion();
        if (version != null) {
            stmt.bindString(15, version);
        }

        String terminalType = entity.getTerminalType();
        if (terminalType != null) {
            stmt.bindString(16, terminalType);
        }
        stmt.bindLong(17, entity.getRefundFloorLimit());

        String refundTacDenial = entity.getRefundTacDenial();
        if (refundTacDenial != null) {
            stmt.bindString(18, refundTacDenial);
        }

        String termTPMCapability = entity.getTermTPMCapability();
        if (termTPMCapability != null) {
            stmt.bindString(19, termTPMCapability);
        }

        String tlvDF810CTag = entity.getTlvDF810CTag();
        if (tlvDF810CTag != null) {
            stmt.bindString(20, tlvDF810CTag);
        }

        String transRecoveryLimit = entity.getTransRecoveryLimit();
        if (transRecoveryLimit != null) {
            stmt.bindString(21, transRecoveryLimit);
        }

        String dataExchangeTagList = entity.getDataExchangeTagList();
        if (dataExchangeTagList != null) {
            stmt.bindString(22, dataExchangeTagList);
        }

        String protocol2Tag82 = entity.getProtocol2Tag82();
        if (protocol2Tag82 != null) {
            stmt.bindString(23, protocol2Tag82);
        }
        stmt.bindLong(24, entity.getExceptFileFlag());
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }

    @Override
    public MirAidBean readEntity(Cursor cursor, int offset) {
        MirAidBean entity = new MirAidBean(
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0),
            cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1),
            cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2),
            (byte) cursor.getShort(offset + 3),
            cursor.getLong(offset + 4),
            (byte) cursor.getShort(offset + 5),
            cursor.getLong(offset + 6),
            (byte) cursor.getShort(offset + 7),
            cursor.getLong(offset + 8),
            (byte) cursor.getShort(offset + 9),
            cursor.isNull(offset + 10) ? null : cursor.getString(offset + 10),
            cursor.isNull(offset + 11) ? null : cursor.getString(offset + 11),
            cursor.isNull(offset + 12) ? null : cursor.getString(offset + 12),
            cursor.isNull(offset + 13) ? null : cursor.getString(offset + 13),
            cursor.isNull(offset + 14) ? null : cursor.getString(offset + 14),
            cursor.isNull(offset + 15) ? null : cursor.getString(offset + 15),
            cursor.getLong(offset + 16),
            cursor.isNull(offset + 17) ? null : cursor.getString(offset + 17),
            cursor.isNull(offset + 18) ? null : cursor.getString(offset + 18),
            cursor.isNull(offset + 19) ? null : cursor.getString(offset + 19),
            cursor.isNull(offset + 20) ? null : cursor.getString(offset + 20),
            cursor.isNull(offset + 21) ? null : cursor.getString(offset + 21),
            cursor.isNull(offset + 22) ? null : cursor.getString(offset + 22),
            (byte) cursor.getShort(offset + 23)
        );
        return entity;
    }

    @Override
    public void readEntity(Cursor cursor, MirAidBean entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setAppName(cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1));
        entity.setAid(cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2));
        entity.setSelFlag((byte) cursor.getShort(offset + 3));
        entity.setTransLimit(cursor.getLong(offset + 4));
        entity.setTransLimitFlag((byte) cursor.getShort(offset + 5));
        entity.setFloorLimit(cursor.getLong(offset + 6));
        entity.setFloorLimitFlag((byte) cursor.getShort(offset + 7));
        entity.setCvmLimit(cursor.getLong(offset + 8));
        entity.setCvmLimitFlag((byte) cursor.getShort(offset + 9));
        entity.setTacDenial(cursor.isNull(offset + 10) ? null : cursor.getString(offset + 10));
        entity.setTacOnline(cursor.isNull(offset + 11) ? null : cursor.getString(offset + 11));
        entity.setTacDefault(cursor.isNull(offset + 12) ? null : cursor.getString(offset + 12));
        entity.setAcquirerId(cursor.isNull(offset + 13) ? null : cursor.getString(offset + 13));
        entity.setVersion(cursor.isNull(offset + 14) ? null : cursor.getString(offset + 14));
        entity.setTerminalType(cursor.isNull(offset + 15) ? null : cursor.getString(offset + 15));
        entity.setRefundFloorLimit(cursor.getLong(offset + 16));
        entity.setRefundTacDenial(cursor.isNull(offset + 17) ? null : cursor.getString(offset + 17));
        entity.setTermTPMCapability(cursor.isNull(offset + 18) ? null : cursor.getString(offset + 18));
        entity.setTlvDF810CTag(cursor.isNull(offset + 19) ? null : cursor.getString(offset + 19));
        entity.setTransRecoveryLimit(cursor.isNull(offset + 20) ? null : cursor.getString(offset + 20));
        entity.setDataExchangeTagList(cursor.isNull(offset + 21) ? null : cursor.getString(offset + 21));
        entity.setProtocol2Tag82(cursor.isNull(offset + 22) ? null : cursor.getString(offset + 22));
        entity.setExceptFileFlag((byte) cursor.getShort(offset + 23));
     }

    @Override
    protected final Long updateKeyAfterInsert(MirAidBean entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    @Override
    public Long getKey(MirAidBean entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(MirAidBean entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }

}
