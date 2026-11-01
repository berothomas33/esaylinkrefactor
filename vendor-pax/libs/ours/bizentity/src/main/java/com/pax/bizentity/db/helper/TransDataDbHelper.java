package com.pax.bizentity.db.helper;

import android.database.Cursor;
import com.pax.bizentity.db.dao.TransDataDao;
import com.pax.bizentity.entity.Acquirer;
import com.pax.bizentity.entity.ETransType;
import com.pax.bizentity.entity.TransData;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.commonlib.utils.ObjectPoolHelper;
import java.util.List;
import org.greenrobot.greendao.query.QueryBuilder;

public class TransDataDbHelper extends BaseDaoHelper<TransData> {
    private static final String SQL_AND = " AND ";
    private static class LazyHolder {
        public static final TransDataDbHelper INSTANCE = new TransDataDbHelper(TransData.class);
    }

    public static TransDataDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public TransDataDbHelper(Class<TransData> entityClass) {
        super(entityClass);
    }

    public final TransData findTransDataByTraceNo(long traceNo) {
        return getNoSessionQuery().where(TransDataDao.Properties.TraceNo.eq(traceNo)
                , TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.NORMAL))
                .unique();
    }

    public final List<TransData> findOfflineTransData(List<TransData.OfflineStatus> status) {
        return getNoSessionQuery().where(TransDataDao.Properties.OfflineSendState.in(status)
                , TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.NORMAL))
                .list();
    }

    public final List<TransData> findTransData(List<String> types, List<TransData.ETransStatus> statuses, Acquirer acq) {
        return getNoSessionQuery().where(TransDataDao.Properties.TransType.in(types)
                , TransDataDao.Properties.TransState.notIn(statuses)
                , TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.NORMAL)
                , TransDataDao.Properties.AcquirerId.eq(acq.getId())).list();
    }

    public final List<TransData> findSettleTransData(List<String> types, List<TransData.ETransStatus> statuses, Acquirer acq) {
        QueryBuilder<TransData> query = getNoSessionQuery();
        query.where(TransDataDao.Properties.TransType.in(types)
                , TransDataDao.Properties.TransState.notIn(statuses)
                , TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.NORMAL)
                , TransDataDao.Properties.AcquirerId.eq(acq.getId())
                , query.or(TransDataDao.Properties.TransType.notEq(ETransType.OFFLINE_TRANS_SEND),
                        query.and(TransDataDao.Properties.TransType.eq(ETransType.OFFLINE_TRANS_SEND),
                                TransDataDao.Properties.OfflineSendState.eq(TransData.OfflineStatus.OFFLINE_SENT))));
        return query.list();
    }

    public final TransData findLastTransData() {
        List<TransData> list = this.loadAll();
        return list != null && !list.isEmpty() ? (TransData) list.get(list.size() - 1) : null;
    }

    public final List<TransData> findAllTransData(boolean includeReversal) {
        return includeReversal ? this.loadAll() : getNoSessionQuery().where(TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.NORMAL)).list();
    }

    public final List<TransData> findAllTransData(Acquirer acq) {
        return this.findAllTransData(acq, false);
    }

    public final List<TransData> findAllTransData(Acquirer acq, boolean includeVoid) {
        QueryBuilder<TransData> builder = getNoSessionQuery().where(TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.NORMAL)
                , TransDataDao.Properties.AcquirerId.eq(acq.getId()));
        if (!includeVoid) {
            builder.where(TransDataDao.Properties.TransState.notEq(TransData.ETransStatus.VOIDED));
        }
        return builder.list();
    }

    public final boolean deleteTransDataByTraceNo(long traceNo) {
        TransData transData = this.findTransDataByTraceNo(traceNo);
        return transData == null || this.delete(transData);
    }

    public final boolean deleteAllTransData() {
        List<TransData> list = this.findAllTransData(true);
        return list == null || list.isEmpty() || this.deleteEntities(list);
    }

    public final boolean deleteAllTransData(Acquirer acq) {
        List<TransData> list = this.findAllTransData(acq, true);
        return list.isEmpty() || this.deleteEntities(list);
    }

    public final long countOf() {
        return getNoSessionQuery().where(TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.NORMAL)).count();
    }

    public final long[] countSumOf(Acquirer acquirer, String type, TransData.ETransStatus status) {
        long[] longArray = new long[]{0L, 0L};
        StringBuilder stringBuilder = ObjectPoolHelper.obtainStringBuilder();
        stringBuilder.append("SELECT count(*),sum(")
                .append(TransDataDao.Properties.Amount.columnName)
                .append(") FROM ")
                .append(TransDataDao.TABLENAME)
                .append(" WHERE ")
                .append(TransDataDao.Properties.TransState.columnName).append(" = '").append(status.toString()).append("'")
                .append(SQL_AND)
                .append(TransDataDao.Properties.ReversalStatus.columnName).append(" = '").append(TransData.ReversalStatus.NORMAL.toString()).append("'")
                .append(SQL_AND)
                .append(TransDataDao.Properties.TransType.columnName).append(" = '").append(type).append("'")
                .append(SQL_AND)
                .append(TransDataDao.Properties.AcquirerId.columnName).append(" = ").append(acquirer.getId());
        Cursor cursor = (Cursor) null;
        try {
            cursor = getDatabase().rawQuery(stringBuilder.toString(), null);
            ObjectPoolHelper.releaseStringBuilder(stringBuilder);
            if (!cursor.moveToFirst()) {
                return longArray;
            }
            longArray[0] = cursor.getInt(0);
            String sum = cursor.getString(1);
            if (sum == null) {
                longArray[1] = 0;
            } else {
                longArray[1] = ConvertUtils.parseLongSafe(sum, 0);
            }
        } catch (Exception e) {
            LogUtils.e(TAG, e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return longArray;
    }

    public final long[] countSumOf(Acquirer acquirer, String type, List<TransData.ETransStatus> status) {
        long[] longArray = new long[]{0L, 0L};
        StringBuilder stateBuilder = ObjectPoolHelper.obtainStringBuilder();
        stateBuilder.append("'");
        for (int index = 0; index < status.size(); index++) {
            if (index != status.size() - 1) {
                stateBuilder.append(status.get(index).toString()).append("','");
            } else {
                stateBuilder.append(status.get(index).toString()).append("'");
            }
        }
        StringBuilder sqlBuilder = ObjectPoolHelper.obtainStringBuilder();
        sqlBuilder.append("SELECT count(*),sum(").append(TransDataDao.Properties.Amount.columnName).append(") FROM ").append(TransDataDao.TABLENAME)
                .append(" WHERE ")
                .append(TransDataDao.Properties.TransState.columnName ).append(" IN(").append(stateBuilder.toString()).append(" )")
                .append(SQL_AND)
                .append(TransDataDao.Properties.ReversalStatus.columnName).append(" = '").append(TransData.ReversalStatus.NORMAL.toString()).append("'")
                .append(SQL_AND)
                .append(TransDataDao.Properties.TransType.columnName).append(" = '").append(type).append("'")
                .append(SQL_AND)
                .append(TransDataDao.Properties.AcquirerId.columnName).append(" = ").append(acquirer.getId());
        Cursor cursor = (Cursor) null;
        try {
            cursor = getDatabase().rawQuery(sqlBuilder.toString(), null);
            ObjectPoolHelper.releaseStringBuilder(stateBuilder);
            ObjectPoolHelper.releaseStringBuilder(sqlBuilder);
            if (!cursor.moveToFirst()) {
                return longArray;
            }
            longArray[0] = cursor.getInt(0);
            String sum = cursor.getString(1);
            if (sum == null) {
                longArray[1] = 0;
            } else {
                longArray[1] = ConvertUtils.parseLongSafe(sum, 0);
            }
        } catch (Exception e) {
            LogUtils.e(TAG, e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return longArray;
    }

    public final long[] countSumOfOffline(Acquirer acquirer, String type, List<TransData.ETransStatus> status) {
        long[] longArray = new long[]{0L, 0L};
        StringBuilder stateBuilder = ObjectPoolHelper.obtainStringBuilder();
        stateBuilder.append("'");
        for (int index = 0; index < status.size(); index++) {
            if (index != status.size() - 1) {
                stateBuilder.append(status.get(index).toString()).append("','");
            } else {
                stateBuilder.append(status.get(index).toString()).append("'");
            }
        }
        StringBuilder sqlBuilder = ObjectPoolHelper.obtainStringBuilder();
        sqlBuilder.append("SELECT count(*),sum(").append(TransDataDao.Properties.Amount.columnName).append(") FROM ").append(TransDataDao.TABLENAME)
                .append(" WHERE ")
                .append(TransDataDao.Properties.TransState.columnName ).append(" IN(").append(stateBuilder.toString()).append(" )")
                .append(SQL_AND)
                .append(TransDataDao.Properties.ReversalStatus.columnName).append(" = '").append(TransData.ReversalStatus.NORMAL.toString()).append("'")
                .append(SQL_AND)
                .append(TransDataDao.Properties.TransType.columnName).append(" = '").append(type).append("'")
                .append(SQL_AND)
                .append(TransDataDao.Properties.AcquirerId.columnName).append(" = ").append(acquirer.getId())
                .append(SQL_AND)
                .append(TransDataDao.Properties.OfflineSendState.columnName).append(" IN('").append(TransData.OfflineStatus.OFFLINE_SENT.toString())
                .append("','").append(TransData.OfflineStatus.OFFLINE_NOT_SENT.toString()).append("')");

        Cursor cursor = (Cursor) null;
        try {
            cursor = getDatabase().rawQuery(sqlBuilder.toString(), null);
            ObjectPoolHelper.releaseStringBuilder(stateBuilder);
            ObjectPoolHelper.releaseStringBuilder(sqlBuilder);
            if (!cursor.moveToFirst()) {
                return longArray;
            }
            longArray[0] = cursor.getInt(0);
            String sum = cursor.getString(1);
            if (sum == null) {
                longArray[1] = 0;
            } else {
                longArray[1] = ConvertUtils.parseLongSafe(sum, 0);
            }
        } catch (Exception e) {
            LogUtils.e(TAG, e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return longArray;
    }

    public final TransData findFirstDupRecord() {
        List<TransData> list = getNoSessionQuery().where(TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.PENDING))
                .list();
        return list != null && !list.isEmpty() ? list.get(list.size() - 1) : null;
    }

    public final List<TransData> findPagingTransData(Acquirer acq,boolean includeVoid,int offset,int limit) {
        QueryBuilder<TransData> builder = getNoSessionQuery().where(TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.NORMAL)
                , TransDataDao.Properties.AcquirerId.eq(acq.getId()));
        if (!includeVoid) {
            builder.where(TransDataDao.Properties.TransState.notEq(TransData.ETransStatus.VOIDED));
        }
        return builder.orderDesc(TransDataDao.Properties.Id).offset(offset).limit(limit).list();
    }

    public final void deleteFirstDupRecord() {
        List<TransData> list = getNoSessionQuery().where(TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.PENDING)).list();
        if (list != null && !list.isEmpty()) {
            this.delete(list.get(list.size() - 1));
        }
    }

    public final boolean deleteAllDupRecord() {
        List<TransData> list = getNoSessionQuery().where(TransDataDao.Properties.ReversalStatus.eq(TransData.ReversalStatus.PENDING)).list();
        return list == null || list.isEmpty() || this.deleteEntities(list);
    }
}
