/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2019-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date	                Author	               Action
 * 20210513 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.bizlib.trans;

import com.pax.bizentity.entity.Acquirer;
import com.pax.bizentity.entity.TransData;
import com.pax.commonlib.currency.CurrencyConverter;
import com.pax.configservice.export.ConfigKeyConstant;
import com.pax.configservice.export.IConfigParamService;
import com.pax.configservice.impl.ConfigParamService;

public class TransactionHelper {
    private static final IConfigParamService configParamService = new ConfigParamService();

    private static final int MAX_TRANS_NO = 999999;
    private static final int MAX_BATCH_NO = 999999;
    private static final String TIME_PATTERN_TRANS = "yyyyMMddHHmmss";

    private TransactionHelper() {
        // do nothing
    }

    /**
     * Is need batch up boolean.
     *
     * @return the boolean
     */
    public static boolean isNeedBatchUp() {
        return configParamService.getInt(ConfigKeyConstant.BATCH_UP_STATUS)  == ConfigKeyConstant.BatchUpStatus.BATCH_UP.getStatus();
    }

    /**
     * 流水号+1
     */
    public static void incTransNo() {
        int transNo = configParamService.getInt(ConfigKeyConstant.EDC_TRACE_NO);
        if (transNo >= MAX_TRANS_NO) {
            transNo = 0;
        }
        transNo++;
        configParamService.putInt(ConfigKeyConstant.EDC_TRACE_NO, transNo);
    }

    /**
     * 批次号+1
     */
    public static void incBatchNo() {
        int batchNo = AcqManager.getInstance().getCurAcq().getCurrBatchNo();
        if (batchNo >= MAX_BATCH_NO) {
            batchNo = 0;
        }
        batchNo++;

        AcqManager.getInstance().getCurAcq().setCurrBatchNo(batchNo);
        AcqManager.getInstance().updateAcquirer(AcqManager.getInstance().getCurAcq());
    }

    /**
     * 交易初始化
     *
     * @return {@link TransData}
     */
    public static TransData transInit() {
        TransData transData = new TransData();
        transInit(transData);
        return transData;
    }

    /**
     * 交易初始化
     *
     * @param transData {@link TransData}
     */
    public static void transInit(TransData transData) {
        Acquirer acquirer = AcqManager.getInstance().getCurAcq();

        transData.setTraceNo(getTransNo());
        transData.setBatchNo(acquirer.getCurrBatchNo());
        transData.setDateTime(Device.getTime(TIME_PATTERN_TRANS));
        transData.setHeader("");
        transData.setTpdu("600" + acquirer.getNii() + "0000");
        // 冲正原因
        transData.setDupReason(TransData.DUP_REASON_OTHERS);
        transData.setTransState(TransData.ETransStatus.NORMAL);
        transData.setAcquirer(AcqManager.getInstance().getCurAcq());
        transData.setCurrency(CurrencyConverter.getDefCurrency());
    }

    // 获取流水号
    private static long getTransNo() {
        int transNo = configParamService.getInt(ConfigKeyConstant.EDC_TRACE_NO);
        if (transNo == 0) {
            transNo += 1;
            configParamService.putInt(ConfigKeyConstant.EDC_TRACE_NO, transNo);
        }
        return transNo;
    }
}
