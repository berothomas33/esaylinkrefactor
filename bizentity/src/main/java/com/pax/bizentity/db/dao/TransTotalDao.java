package com.pax.bizentity.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import com.pax.bizentity.entity.TransTotal;

public class TransTotalDao extends AbstractDao<TransTotal, Long> {

    public static final String TABLENAME = "trans_total";

    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "id");
        public final static Property MerchantID = new Property(1, String.class, "merchantID", false, "mid");
        public final static Property TerminalID = new Property(2, String.class, "terminalID", false, "tid");
        public final static Property BatchNo = new Property(3, int.class, "batchNo", false, "batch_no");
        public final static Property DateTime = new Property(4, String.class, "dateTime", false, "batch_time");
        public final static Property AcquirerId = new Property(5, long.class, "acquirerId", false, "ACQUIRER_ID");
        public final static Property IsClosed = new Property(6, boolean.class, "isClosed", false, "closed");
        public final static Property SaleTotalAmt = new Property(7, long.class, "saleTotalAmt", false, "SALE_AMOUNT");
        public final static Property SaleTotalNum = new Property(8, long.class, "saleTotalNum", false, "SALE_NUM");
        public final static Property VoidTotalAmt = new Property(9, long.class, "voidTotalAmt", false, "VOID_AMOUNT");
        public final static Property VoidTotalNum = new Property(10, long.class, "voidTotalNum", false, "VOID_NUM");
        public final static Property RefundTotalAmt = new Property(11, long.class, "refundTotalAmt", false, "REFUND_AMOUNT");
        public final static Property RefundTotalNum = new Property(12, long.class, "refundTotalNum", false, "REFUND_NUM");
        public final static Property RefundVoidTotalAmt = new Property(13, long.class, "refundVoidTotalAmt", false, "REFUND_VOID_AMOUNT");
        public final static Property RefundVoidTotalNum = new Property(14, long.class, "refundVoidTotalNum", false, "REFUND_VOID_NUM");
        public final static Property SaleVoidTotalAmt = new Property(15, long.class, "saleVoidTotalAmt", false, "SALE_VOID_AMOUNT");
        public final static Property SaleVoidTotalNum = new Property(16, long.class, "saleVoidTotalNum", false, "SALE_VOID_NUM");
        public final static Property AuthTotalAmt = new Property(17, long.class, "authTotalAmt", false, "AUTH_AMOUNT");
        public final static Property AuthTotalNum = new Property(18, long.class, "authTotalNum", false, "AUTH_NUM");
        public final static Property OfflineTotalAmt = new Property(19, long.class, "offlineTotalAmt", false, "OFFLINE_AMOUNT");
        public final static Property OfflineTotalNum = new Property(20, long.class, "offlineTotalNum", false, "OFFLINE_NUM");
    }

    public TransTotalDao(DaoConfig config) {
        super(config);
    }

    public TransTotalDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"trans_total\" (" +
                "\"id\" INTEGER PRIMARY KEY AUTOINCREMENT ," +
                "\"mid\" TEXT," +
                "\"tid\" TEXT," +
                "\"batch_no\" INTEGER NOT NULL ," +
                "\"batch_time\" TEXT," +
                "\"ACQUIRER_ID\" INTEGER NOT NULL ," +
                "\"closed\" INTEGER NOT NULL ," +
                "\"SALE_AMOUNT\" INTEGER NOT NULL ," +
                "\"SALE_NUM\" INTEGER NOT NULL ," +
                "\"VOID_AMOUNT\" INTEGER NOT NULL ," +
                "\"VOID_NUM\" INTEGER NOT NULL ," +
                "\"REFUND_AMOUNT\" INTEGER NOT NULL ," +
                "\"REFUND_NUM\" INTEGER NOT NULL ," +
                "\"REFUND_VOID_AMOUNT\" INTEGER NOT NULL ," +
                "\"REFUND_VOID_NUM\" INTEGER NOT NULL ," +
                "\"SALE_VOID_AMOUNT\" INTEGER NOT NULL ," +
                "\"SALE_VOID_NUM\" INTEGER NOT NULL ," +
                "\"AUTH_AMOUNT\" INTEGER NOT NULL ," +
                "\"AUTH_NUM\" INTEGER NOT NULL ," +
                "\"OFFLINE_AMOUNT\" INTEGER NOT NULL ," +
                "\"OFFLINE_NUM\" INTEGER NOT NULL );");
    }

    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"trans_total\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, TransTotal entity) {
        stmt.clearBindings();

        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }

        String merchantID = entity.getMerchantID();
        if (merchantID != null) {
            stmt.bindString(2, merchantID);
        }

        String terminalID = entity.getTerminalID();
        if (terminalID != null) {
            stmt.bindString(3, terminalID);
        }
        stmt.bindLong(4, entity.getBatchNo());

        String dateTime = entity.getDateTime();
        if (dateTime != null) {
            stmt.bindString(5, dateTime);
        }
        stmt.bindLong(6, entity.getAcquirerId());
        stmt.bindLong(7, entity.getIsClosed() ? 1L: 0L);
        stmt.bindLong(8, entity.getSaleTotalAmt());
        stmt.bindLong(9, entity.getSaleTotalNum());
        stmt.bindLong(10, entity.getVoidTotalAmt());
        stmt.bindLong(11, entity.getVoidTotalNum());
        stmt.bindLong(12, entity.getRefundTotalAmt());
        stmt.bindLong(13, entity.getRefundTotalNum());
        stmt.bindLong(14, entity.getRefundVoidTotalAmt());
        stmt.bindLong(15, entity.getRefundVoidTotalNum());
        stmt.bindLong(16, entity.getSaleVoidTotalAmt());
        stmt.bindLong(17, entity.getSaleVoidTotalNum());
        stmt.bindLong(18, entity.getAuthTotalAmt());
        stmt.bindLong(19, entity.getAuthTotalNum());
        stmt.bindLong(20, entity.getOfflineTotalAmt());
        stmt.bindLong(21, entity.getOfflineTotalNum());
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, TransTotal entity) {
        stmt.clearBindings();

        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }

        String merchantID = entity.getMerchantID();
        if (merchantID != null) {
            stmt.bindString(2, merchantID);
        }

        String terminalID = entity.getTerminalID();
        if (terminalID != null) {
            stmt.bindString(3, terminalID);
        }
        stmt.bindLong(4, entity.getBatchNo());

        String dateTime = entity.getDateTime();
        if (dateTime != null) {
            stmt.bindString(5, dateTime);
        }
        stmt.bindLong(6, entity.getAcquirerId());
        stmt.bindLong(7, entity.getIsClosed() ? 1L: 0L);
        stmt.bindLong(8, entity.getSaleTotalAmt());
        stmt.bindLong(9, entity.getSaleTotalNum());
        stmt.bindLong(10, entity.getVoidTotalAmt());
        stmt.bindLong(11, entity.getVoidTotalNum());
        stmt.bindLong(12, entity.getRefundTotalAmt());
        stmt.bindLong(13, entity.getRefundTotalNum());
        stmt.bindLong(14, entity.getRefundVoidTotalAmt());
        stmt.bindLong(15, entity.getRefundVoidTotalNum());
        stmt.bindLong(16, entity.getSaleVoidTotalAmt());
        stmt.bindLong(17, entity.getSaleVoidTotalNum());
        stmt.bindLong(18, entity.getAuthTotalAmt());
        stmt.bindLong(19, entity.getAuthTotalNum());
        stmt.bindLong(20, entity.getOfflineTotalAmt());
        stmt.bindLong(21, entity.getOfflineTotalNum());
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }

    @Override
    public TransTotal readEntity(Cursor cursor, int offset) {
        TransTotal entity = new TransTotal(
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0),
            cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1),
            cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2),
            cursor.getInt(offset + 3),
            cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4),
            cursor.getLong(offset + 5),
            cursor.getShort(offset + 6) != 0,
            cursor.getLong(offset + 7),
            cursor.getLong(offset + 8),
            cursor.getLong(offset + 9),
            cursor.getLong(offset + 10),
            cursor.getLong(offset + 11),
            cursor.getLong(offset + 12),
            cursor.getLong(offset + 13),
            cursor.getLong(offset + 14),
            cursor.getLong(offset + 15),
            cursor.getLong(offset + 16),
            cursor.getLong(offset + 17),
            cursor.getLong(offset + 18),
            cursor.getLong(offset + 19),
            cursor.getLong(offset + 20)
        );
        return entity;
    }

    @Override
    public void readEntity(Cursor cursor, TransTotal entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setMerchantID(cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1));
        entity.setTerminalID(cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2));
        entity.setBatchNo(cursor.getInt(offset + 3));
        entity.setDateTime(cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4));
        entity.setAcquirerId(cursor.getLong(offset + 5));
        entity.setIsClosed(cursor.getShort(offset + 6) != 0);
        entity.setSaleTotalAmt(cursor.getLong(offset + 7));
        entity.setSaleTotalNum(cursor.getLong(offset + 8));
        entity.setVoidTotalAmt(cursor.getLong(offset + 9));
        entity.setVoidTotalNum(cursor.getLong(offset + 10));
        entity.setRefundTotalAmt(cursor.getLong(offset + 11));
        entity.setRefundTotalNum(cursor.getLong(offset + 12));
        entity.setRefundVoidTotalAmt(cursor.getLong(offset + 13));
        entity.setRefundVoidTotalNum(cursor.getLong(offset + 14));
        entity.setSaleVoidTotalAmt(cursor.getLong(offset + 15));
        entity.setSaleVoidTotalNum(cursor.getLong(offset + 16));
        entity.setAuthTotalAmt(cursor.getLong(offset + 17));
        entity.setAuthTotalNum(cursor.getLong(offset + 18));
        entity.setOfflineTotalAmt(cursor.getLong(offset + 19));
        entity.setOfflineTotalNum(cursor.getLong(offset + 20));
     }

    @Override
    protected final Long updateKeyAfterInsert(TransTotal entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    @Override
    public Long getKey(TransTotal entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(TransTotal entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }

}
