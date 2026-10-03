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
 * 20210508 	        xieYb                  Create
 * ===========================================================================================
 *
 */
package com.pax.bizentity.entity;

import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;

/**
 * 交易总计
 *
 * @author Steven.W
 */

@Entity(nameInDb = "trans_total")
public class TransTotal implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String ID_FIELD_NAME = "id";
    public static final String IS_CLOSED_FIELD_NAME = "closed";
    public static final String MID_FIELD_NAME = "mid";
    public static final String TID_FIELD_NAME = "tid";
    public static final String BATCHNO_FIELD_NAME = "batch_no";
    public static final String TIME_FIELD_NAME = "batch_time";

    public static final String SALE_AMOUNT = "SALE_AMOUNT";
    public static final String SALE_NUM = "SALE_NUM";
    public static final String VOID_AMOUNT = "VOID_AMOUNT";
    public static final String VOID_NUM = "VOID_NUM";
    public static final String REFUND_AMOUNT = "REFUND_AMOUNT";
    public static final String REFUND_NUM = "REFUND_NUM";
    public static final String REFUND_VOID_AMOUNT = "REFUND_VOID_AMOUNT";
    public static final String REFUND_VOID_NUM = "REFUND_VOID_NUM";
    public static final String SALE_VOID_AMOUNT = "SALE_VOID_AMOUNT";
    public static final String SALE_VOID_NUM = "SALE_VOID_NUM";
    public static final String AUTH_AMOUNT = "AUTH_AMOUNT";
    public static final String AUTH_NUM = "AUTH_NUM";
    public static final String OFFLINE_AMOUNT = "OFFLINE_AMOUNT";
    public static final String OFFLINE_NUM = "OFFLINE_NUM";


    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    protected Long id;

    /**
     * 商户号
     */
    @Property(nameInDb = MID_FIELD_NAME)
    private String merchantID;
    /**
     * 终端号
     */
    @Property(nameInDb = TID_FIELD_NAME)
    private String terminalID;
    /**
     * 批次号
     */
    @Property(nameInDb = BATCHNO_FIELD_NAME)
    private int batchNo;

    /**
     * 日期时间
     */
    @Property(nameInDb = TIME_FIELD_NAME)
    private String dateTime;

    // Plain reference (no join): load the acquirer by id when needed.
    private long acquirerId;

    @Property(nameInDb = IS_CLOSED_FIELD_NAME)
    private boolean isClosed;

    /**
     * 消费总金额
     */
    @Property(nameInDb = SALE_AMOUNT)
    private long saleTotalAmt;
    /**
     * 消费总笔数
     */
    @Property(nameInDb = SALE_NUM)
    private long saleTotalNum;

    /**
     * 撤销总金额
     */
    @Property(nameInDb = VOID_AMOUNT)
    private long voidTotalAmt;
    /**
     * 撤销总笔数
     */
    @Property(nameInDb = VOID_NUM)
    private long voidTotalNum;
    /**
     * 退货总金额
     */
    @Property(nameInDb = REFUND_AMOUNT)
    private long refundTotalAmt;
    /**
     * 退货总笔数
     */
    @Property(nameInDb = REFUND_NUM)
    private long refundTotalNum;
    /**
     * refund void total amount
     */
    @Property(nameInDb = REFUND_VOID_AMOUNT)
    private long refundVoidTotalAmt;
    /**
     * refund void total num
     */
    @Property(nameInDb = REFUND_VOID_NUM)
    private long refundVoidTotalNum;
    /**
     * sale void total amount
     */
    @Property(nameInDb = SALE_VOID_AMOUNT)
    private long saleVoidTotalAmt;
    /**
     * sale void total num
     */
    @Property(nameInDb = SALE_VOID_NUM)
    private long saleVoidTotalNum;
    /**
     * 预授权总金额
     */
    @Property(nameInDb = AUTH_AMOUNT)
    private long authTotalAmt;
    /**
     * 预授权总笔数
     */
    @Property(nameInDb = AUTH_NUM)
    private long authTotalNum;
    //AET-75
    /**
     * 脱机交易总金额
     */
    @Property(nameInDb = OFFLINE_AMOUNT)
    private long offlineTotalAmt;
    /**
     * 脱机交易总笔数
     */
    @Property(nameInDb = OFFLINE_NUM)
    private long offlineTotalNum;


    @Generated(hash = 1774638778)
    public TransTotal(Long id, String merchantID, String terminalID, int batchNo, String dateTime, long acquirerId, boolean isClosed,
            long saleTotalAmt, long saleTotalNum, long voidTotalAmt, long voidTotalNum, long refundTotalAmt, long refundTotalNum,
            long refundVoidTotalAmt, long refundVoidTotalNum, long saleVoidTotalAmt, long saleVoidTotalNum, long authTotalAmt,
            long authTotalNum, long offlineTotalAmt, long offlineTotalNum) {
        this.id = id;
        this.merchantID = merchantID;
        this.terminalID = terminalID;
        this.batchNo = batchNo;
        this.dateTime = dateTime;
        this.acquirerId = acquirerId;
        this.isClosed = isClosed;
        this.saleTotalAmt = saleTotalAmt;
        this.saleTotalNum = saleTotalNum;
        this.voidTotalAmt = voidTotalAmt;
        this.voidTotalNum = voidTotalNum;
        this.refundTotalAmt = refundTotalAmt;
        this.refundTotalNum = refundTotalNum;
        this.refundVoidTotalAmt = refundVoidTotalAmt;
        this.refundVoidTotalNum = refundVoidTotalNum;
        this.saleVoidTotalAmt = saleVoidTotalAmt;
        this.saleVoidTotalNum = saleVoidTotalNum;
        this.authTotalAmt = authTotalAmt;
        this.authTotalNum = authTotalNum;
        this.offlineTotalAmt = offlineTotalAmt;
        this.offlineTotalNum = offlineTotalNum;
    }

    @Generated(hash = 1642661961)
    public TransTotal() {
    }



    public boolean isZero() {
        return (getSaleTotalNum() + getRefundTotalNum() + getSaleVoidTotalNum() + getRefundVoidTotalNum() + getOfflineTotalNum()) == 0;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void setClosed(boolean closed) {
        isClosed = closed;
    }

    public Long getId() {
        return this.id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public String getMerchantID() {
        return this.merchantID;
    }


    public void setMerchantID(String merchantID) {
        this.merchantID = merchantID;
    }


    public String getTerminalID() {
        return this.terminalID;
    }


    public void setTerminalID(String terminalID) {
        this.terminalID = terminalID;
    }


    public int getBatchNo() {
        return this.batchNo;
    }


    public void setBatchNo(int batchNo) {
        this.batchNo = batchNo;
    }


    public String getDateTime() {
        return this.dateTime;
    }


    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }


    public boolean getIsClosed() {
        return this.isClosed;
    }


    public void setIsClosed(boolean isClosed) {
        this.isClosed = isClosed;
    }



    public long getSaleTotalAmt() {
        return this.saleTotalAmt;
    }


    public void setSaleTotalAmt(long saleTotalAmt) {
        this.saleTotalAmt = saleTotalAmt;
    }


    public long getSaleTotalNum() {
        return this.saleTotalNum;
    }


    public void setSaleTotalNum(long saleTotalNum) {
        this.saleTotalNum = saleTotalNum;
    }


    public long getVoidTotalAmt() {
        return this.voidTotalAmt;
    }


    public void setVoidTotalAmt(long voidTotalAmt) {
        this.voidTotalAmt = voidTotalAmt;
    }


    public long getVoidTotalNum() {
        return this.voidTotalNum;
    }


    public void setVoidTotalNum(long voidTotalNum) {
        this.voidTotalNum = voidTotalNum;
    }


    public long getRefundTotalAmt() {
        return this.refundTotalAmt;
    }


    public void setRefundTotalAmt(long refundTotalAmt) {
        this.refundTotalAmt = refundTotalAmt;
    }


    public long getRefundTotalNum() {
        return this.refundTotalNum;
    }


    public void setRefundTotalNum(long refundTotalNum) {
        this.refundTotalNum = refundTotalNum;
    }



    public long getRefundVoidTotalAmt() {
        return this.refundVoidTotalAmt;
    }


    public void setRefundVoidTotalAmt(long refundVoidTotalAmt) {
        this.refundVoidTotalAmt = refundVoidTotalAmt;
    }


    public long getRefundVoidTotalNum() {
        return this.refundVoidTotalNum;
    }


    public void setRefundVoidTotalNum(long refundVoidTotalNum) {
        this.refundVoidTotalNum = refundVoidTotalNum;
    }


    public long getSaleVoidTotalAmt() {
        return this.saleVoidTotalAmt;
    }


    public void setSaleVoidTotalAmt(long saleVoidTotalAmt) {
        this.saleVoidTotalAmt = saleVoidTotalAmt;
    }


    public long getSaleVoidTotalNum() {
        return this.saleVoidTotalNum;
    }


    public void setSaleVoidTotalNum(long saleVoidTotalNum) {
        this.saleVoidTotalNum = saleVoidTotalNum;
    }



    public long getAuthTotalAmt() {
        return this.authTotalAmt;
    }


    public void setAuthTotalAmt(long authTotalAmt) {
        this.authTotalAmt = authTotalAmt;
    }


    public long getAuthTotalNum() {
        return this.authTotalNum;
    }


    public void setAuthTotalNum(long authTotalNum) {
        this.authTotalNum = authTotalNum;
    }



    public long getOfflineTotalAmt() {
        return this.offlineTotalAmt;
    }


    public void setOfflineTotalAmt(long offlineTotalAmt) {
        this.offlineTotalAmt = offlineTotalAmt;
    }


    public long getOfflineTotalNum() {
        return this.offlineTotalNum;
    }


    public void setOfflineTotalNum(long offlineTotalNum) {
        this.offlineTotalNum = offlineTotalNum;
    }


    public long getAcquirerId() {
        return this.acquirerId;
    }

    public void setAcquirerId(long acquirerId) {
        this.acquirerId = acquirerId;
    }
}
