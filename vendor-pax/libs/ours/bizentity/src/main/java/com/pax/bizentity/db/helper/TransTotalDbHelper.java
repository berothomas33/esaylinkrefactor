package com.pax.bizentity.db.helper;

import com.pax.bizentity.db.dao.TransTotalDao;
import com.pax.bizentity.entity.Acquirer;
import com.pax.bizentity.entity.ETransType;
import com.pax.bizentity.entity.TransData;
import com.pax.bizentity.entity.TransTotal;
import java.util.ArrayList;
import java.util.List;
import org.greenrobot.greendao.query.QueryBuilder;

public class TransTotalDbHelper extends BaseDaoHelper<TransTotal> {
    private static class LazyHolder {
        public static final TransTotalDbHelper INSTANCE = new TransTotalDbHelper(TransTotal.class);
    }

    public static TransTotalDbHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    public TransTotalDbHelper(Class<TransTotal> entityClass) {
        super(entityClass);
    }

    public final TransTotal findLastTransTotal(Acquirer acquirer, boolean isClosed) {
        List<TransTotal> list = this.findAllTransTotal(acquirer, isClosed);
        return list != null && !list.isEmpty() ? list.get(list.size() - 1) : null;
    }

    public final List<TransTotal> findAllTransTotal(Acquirer acquirer, boolean isClosed) {
        QueryBuilder<TransTotal> builder = getNoSessionQuery().where(TransTotalDao.Properties.IsClosed.eq(isClosed));
        if (acquirer != null) {
            builder.where(TransTotalDao.Properties.AcquirerId.eq(acquirer.getId()));
        }
        return builder.list();
    }

    public final TransTotal calcTotal(Acquirer acquirer) {
        ArrayList<TransData.ETransStatus> filter = new ArrayList<>();
        filter.add(TransData.ETransStatus.NORMAL);
        filter.add(TransData.ETransStatus.ADJUSTED);
        TransTotal total = new TransTotal();

        total.setBatchNo(acquirer.getCurrBatchNo());
        total.setTerminalID(acquirer.getTerminalId());
        total.setMerchantID(acquirer.getMerchantId());

        long[] obj = TransDataDbHelper.getInstance().countSumOf(acquirer, ETransType.SALE.name(), filter);
        total.setSaleTotalNum(obj[0]);
        total.setSaleTotalAmt(obj[1]);

        obj = TransDataDbHelper.getInstance().countSumOf(acquirer, ETransType.VOID.name(), TransData.ETransStatus.NORMAL);
        total.setVoidTotalNum(obj[0]);
        total.setVoidTotalAmt(obj[1]);

        obj = TransDataDbHelper.getInstance().countSumOf(acquirer, ETransType.REFUND.name(), TransData.ETransStatus.NORMAL);
        total.setRefundTotalNum(obj[0]);
        total.setRefundTotalAmt(obj[1]);

        obj = TransDataDbHelper.getInstance().countSumOf(acquirer, ETransType.SALE.name(), TransData.ETransStatus.VOIDED);
        total.setSaleVoidTotalNum(obj[0]);
        total.setSaleVoidTotalAmt(obj[1]);
        obj = TransDataDbHelper.getInstance().countSumOf(acquirer, ETransType.REFUND.name(), TransData.ETransStatus.VOIDED);
        total.setRefundVoidTotalNum(obj[0]);
        total.setRefundVoidTotalAmt(obj[1]);

        obj = TransDataDbHelper.getInstance().countSumOf(acquirer, ETransType.PREAUTH.name(), TransData.ETransStatus.NORMAL);
        total.setAuthTotalNum(obj[0]);
        total.setAuthTotalAmt(obj[1]);

        obj = TransDataDbHelper.getInstance().countSumOfOffline(acquirer, ETransType.OFFLINE_TRANS_SEND.name(), filter);
        total.setOfflineTotalNum(obj[0]);
        total.setOfflineTotalAmt(obj[1]);
        total.setAcquirerId(acquirer.getId());

        return total;
    }
}
