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

import androidx.annotation.Nullable;
import com.alibaba.fastjson.JSON;
import com.pax.bizentity.db.dao.AcquirerDao;
import com.pax.bizentity.db.dao.DaoSession;
import com.pax.bizentity.db.dao.IssuerDao;
import com.pax.bizentity.db.dao.TransDataDao;
import com.pax.bizentity.db.helper.DaoManager;
import java.io.Serializable;
import java.util.Locale;
import org.greenrobot.greendao.DaoException;
import org.greenrobot.greendao.annotation.Convert;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Keep;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.ToOne;
import org.greenrobot.greendao.annotation.Transient;
import org.greenrobot.greendao.annotation.Unique;
import org.greenrobot.greendao.converter.PropertyConverter;

@Entity(nameInDb = "trans_data")
public class TransData implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String ID_FIELD_NAME = "id";
    public static final String TRACENO_FIELD_NAME = "trace_no";
    public static final String BATCHNO_FIELD_NAME = "batch_no";
    public static final String TYPE_FIELD_NAME = "type";
    public static final String STATE_FIELD_NAME = "state";
    public static final String OFFLINE_STATE_FIELD_NAME = "offline_state";
    public static final String AMOUNT_FIELD_NAME = "amount";
    public static final String REVERSAL_FIELD_NAME = "REVERSAL";
    public static final String SIGN_PATH = "sign_path";

    /**
     * 交易状态
     *
     * @author Steven.W
     */
    public enum ETransStatus {
        /**
         * 正常
         */
        NORMAL,
        /**
         * 已撤销
         */
        VOIDED,
        /**
         * 已调整
         */
        ADJUSTED
    }

    /* 脱机上送失败原因 */

    public enum OfflineStatus {
        /**
         * offline not sent
         */
        OFFLINE_NOT_SENT(0),
        /**
         * offline sent
         */
        OFFLINE_SENT(1),
        /**
         * 脱机上送失败
         */
        OFFLINE_ERR_SEND(2),
        /**
         * 脱机上送平台拒绝(返回码非00)
         */
        OFFLINE_ERR_RESP(3),
        /**
         * 脱机上送未知失败原因
         */
        OFFLINE_ERR_UNKNOWN(0xff),;

        private final int value;

        OfflineStatus(int value) {
            this.value = value;
        }
    }

    /**
     * 电子签名上送状态
     */
    public enum SignSendStatus {
        /**
         * 未上送
         */
        SEND_SIG_NO,
        /**
         * 上送成功
         */
        SEND_SIG_SUCC,
        /**
         * 上送失败
         */
        SEND_SIG_ERR,
    }

    public enum EnterMode {
        /**
         * 手工输入
         */
        MANUAL("M"),
        /**
         * 刷卡
         */
        SWIPE("S"),
        /**
         * 插卡
         */
        INSERT("I"),
        /**
         * IC卡回退
         */
        FALLBACK("F"),
        /**
         * 非接支付
         */
        CLSS("C"),
        /**
         * 扫码支付
         */
        QR("Q"),;

        private String str;

        private EnterMode(String str) {
            this.str = str;
        }

        @Override
        public String toString() {
            return str;
        }
    }

    public enum ReversalStatus {
        /**
         * No need for reversal
         */
        NORMAL,
        /**
         * Need reversal
         */
        PENDING,
    }

    public static final String DUP_REASON_NO_RECV = "98";
    public static final String DUP_REASON_MACWRONG = "A0";
    public static final String DUP_REASON_OTHERS = "06";
    public static final String DUP_REASON_CARD_DENIED = "AA";
    public static final String DUP_REASON_RUPAY_E1 = "E1";
    public static final String DUP_REASON_RUPAY_E2 = "E2";
    public static final String DUP_REASON_RUPAY_22 = "22";

    // ============= 需要存储 ==========================
    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    protected Long id;
    @Property(nameInDb = TRACENO_FIELD_NAME)
    @Unique
    @NotNull
    protected long traceNo; // pos流水号
    protected long origTransNo; // 原pos流水号
    @Property(nameInDb = TYPE_FIELD_NAME)
    @NotNull
    protected String transType; // 交易类型
    protected String origTransType = null; // 原交易类型
    @Property(nameInDb = STATE_FIELD_NAME)
    @NotNull
    @Convert(converter = ETransStatusConverter.class, columnType = String.class)
    protected ETransStatus transState = ETransStatus.NORMAL; // 交易状态
    @NotNull
    protected boolean isUpload = false; // 是否已批上送
    @Property(nameInDb = OFFLINE_STATE_FIELD_NAME)
    @Convert(converter = OfflineStatusConverter.class, columnType = String.class)
    protected OfflineStatus offlineSendState = null; // 脱机上送失败类型 ：上送失败/平台拒绝
    @NotNull
    protected int sendTimes; // 已批上送次数
    protected String procCode; // 处理码，39域
    @Property(nameInDb = AMOUNT_FIELD_NAME)
    protected String amount; // 交易总金额
    protected String tipAmount; // 小费金额
    @Convert(converter = LocaleConverter.class, columnType = String.class)
    protected Locale currency; // currency
    @Property(nameInDb = BATCHNO_FIELD_NAME)
    @NotNull
    protected long batchNo; // 批次号
    protected long origBatchNo; // 原批次号
    protected String pan; // 主账号-刷两张卡时为转出卡卡号
    protected String dateTime; // 交易日期时间
    protected String origDateTime; // 原交易日期时间
    protected String settleDateTime; // 清算日期时间
    protected String expDate; // 卡有效期
    @Convert(converter = EnterModeConverter.class, columnType = String.class)
    protected EnterMode enterMode; // 输入模式
    protected String nii;     //Network International Identifier
    protected String refNo; // 系统参考号
    protected String origRefNo; // 原系统参考号
    protected String authCode; // 授权码
    protected String origAuthCode; // 原授权码
    protected String issuerCode; // 发卡行标识码
    protected String acqCode; // 收单机构标识码
    @NotNull
    protected boolean hasPin; // 是否有输密码
    protected String track1; // 磁道一信息
    protected String track2; // 磁道二数据
    protected String track3; // 磁道三数据
    protected String dupReason; // 冲正原因
    protected String reserved; // 保留域[field63]

    // =================EMV数据=============================
    @NotNull
    protected boolean pinFree = false; // 免密
    @NotNull
    protected boolean signFree = false; // 免签
    @NotNull
    protected boolean isCDCVM = false; // CDCVM标识
    @NotNull
    protected boolean isOnlineTrans = false; // 是否为联机交易
    // 电子签名专用
    protected byte[] signData; // signData

    // 电子签名专用
    @Property(nameInDb = SIGN_PATH)
    private byte[] signPath; // raw sign path

    private long issuer_id;
    @ToOne(joinProperty = "issuer_id")
    protected Issuer issuer;

    private long acquirer_id;
    @ToOne(joinProperty = "acquirer_id")
    protected Acquirer acquirer;

    // =================EMV数据=============================
    protected String emvResult = null; // EMV交易的执行状态
    protected String cardSerialNo; // 23 域，卡片序列号
    protected String sendIccData; // IC卡信息,55域
    protected String dupIccData; // IC卡冲正信息,55域
    protected String tc; // IC卡交易证书(TC值)tag9f26,(BIN)
    protected String arqc; // 授权请求密文(ARQC)
    protected String arpc; // 授权响应密文(ARPC)
    protected String tvr; // 终端验证结果(TVR)值tag95
    protected String aid; // 应用标识符AID
    protected String emvAppLabel; // 应用标签
    protected String emvAppName; // 应用首选名称
    protected String tsi; // 交易状态信息(TSI)tag9B
    protected String atc; // 应用交易计数器(ATC)值tag9f36

    @Property(nameInDb = REVERSAL_FIELD_NAME)
    @NotNull
    @Convert(converter = ReversalStatusConverter.class, columnType = String.class)
    protected ReversalStatus reversalStatus = ReversalStatus.NORMAL;

    private String phoneNum;

    private String email;
    protected String maskPan;
    protected String panBlock;

    // ================不需要存储=============================
    @Transient
    private String pin;
    @Transient
    protected String responseCode;
    @Transient
    protected String header;
    @Transient
    protected String tpdu;

    @Transient
    protected String field48;
    @Transient
    protected String field60;
    @Transient
    protected String field62;
    @Transient
    protected String field63;
    @Transient
    protected String recvIccData;
    @Transient
    protected String field3;

    /** Used to resolve relations */
    @Generated(hash = 2040040024)
    private transient DaoSession daoSession;

    /** Used for active entity operations. */
    @Generated(hash = 1906158038)
    private transient TransDataDao myDao;

    @Generated(hash = 1724583466)
    private transient Long issuer__resolvedKey;

    @Generated(hash = 86676445)
    private transient Long acquirer__resolvedKey;


    public TransData(){

    }

    //copy constructor, replace clone
    public TransData(TransData other) {
        this.id = other.id;
        this.traceNo = other.traceNo;
        this.origTransNo = other.origTransNo;
        this.transType = other.transType;
        this.origTransType = other.origTransType;
        this.transState = other.transState;
        this.isUpload = other.isUpload;
        this.offlineSendState = other.offlineSendState;
        this.sendTimes = other.sendTimes;
        this.procCode = other.procCode;
        this.amount = other.amount;
        this.tipAmount = other.tipAmount;
        this.batchNo = other.batchNo;
        this.origBatchNo = other.origBatchNo;
        this.pan = other.pan;
        this.dateTime = other.dateTime;
        this.origDateTime = other.origDateTime;
        this.settleDateTime = other.settleDateTime;
        this.expDate = other.expDate;
        this.nii = other.nii;
        this.refNo = other.refNo;
        this.origRefNo = other.origRefNo;
        this.authCode = other.authCode;
        this.origAuthCode = other.origAuthCode;
        this.issuerCode = other.issuerCode;
        this.acqCode = other.acqCode;
        this.hasPin = other.hasPin;
        this.track1 = other.track1;
        this.track2 = other.track2;
        this.track3 = other.track3;
        this.dupReason = other.dupReason;
        this.reserved = other.reserved;
        this.pinFree = other.pinFree;
        this.signFree = other.signFree;
        this.isCDCVM = other.isCDCVM;
        this.isOnlineTrans = other.isOnlineTrans;
        this.signData = other.signData;
        this.signPath = other.signPath;
        this.cardSerialNo = other.cardSerialNo;
        this.sendIccData = other.sendIccData;
        this.dupIccData = other.dupIccData;
        this.tc = other.tc;
        this.arqc = other.arqc;
        this.arpc = other.arpc;
        this.tvr = other.tvr;
        this.aid = other.aid;
        this.emvAppLabel = other.emvAppLabel;
        this.emvAppName = other.emvAppName;
        this.tsi = other.tsi;
        this.atc = other.atc;
        this.reversalStatus = other.reversalStatus;
        this.phoneNum = other.phoneNum;
        this.email = other.email;
        this.pin = other.pin;
        this.header = other.header;
        this.tpdu = other.tpdu;
        this.field48 = other.field48;
        this.field60 = other.field60;
        this.field62 = other.field62;
        this.field63 = other.field63;
        this.recvIccData = other.recvIccData;
        this.field3 = other.field3;

        this.emvResult = other.emvResult;
        this.enterMode = other.enterMode;
        this.issuer = other.issuer;
        this.maskPan = other.maskPan;
        this.panBlock = other.panBlock;
    }

    @Generated(hash = 57647614)
    public TransData(Long id, long traceNo, long origTransNo, @NotNull String transType,
            String origTransType, @NotNull ETransStatus transState, boolean isUpload,
            OfflineStatus offlineSendState, int sendTimes, String procCode, String amount,
            String tipAmount, Locale currency, long batchNo, long origBatchNo, String pan,
            String dateTime, String origDateTime, String settleDateTime, String expDate,
            EnterMode enterMode, String nii, String refNo, String origRefNo, String authCode,
            String origAuthCode, String issuerCode, String acqCode, boolean hasPin, String track1,
            String track2, String track3, String dupReason, String reserved, boolean pinFree,
            boolean signFree, boolean isCDCVM, boolean isOnlineTrans, byte[] signData, byte[] signPath,
            long issuer_id, long acquirer_id, String emvResult, String cardSerialNo, String sendIccData,
            String dupIccData, String tc, String arqc, String arpc, String tvr, String aid,
            String emvAppLabel, String emvAppName, String tsi, String atc,
            @NotNull ReversalStatus reversalStatus, String phoneNum, String email, String maskPan,
            String panBlock) {
        this.id = id;
        this.traceNo = traceNo;
        this.origTransNo = origTransNo;
        this.transType = transType;
        this.origTransType = origTransType;
        this.transState = transState;
        this.isUpload = isUpload;
        this.offlineSendState = offlineSendState;
        this.sendTimes = sendTimes;
        this.procCode = procCode;
        this.amount = amount;
        this.tipAmount = tipAmount;
        this.currency = currency;
        this.batchNo = batchNo;
        this.origBatchNo = origBatchNo;
        this.pan = pan;
        this.dateTime = dateTime;
        this.origDateTime = origDateTime;
        this.settleDateTime = settleDateTime;
        this.expDate = expDate;
        this.enterMode = enterMode;
        this.nii = nii;
        this.refNo = refNo;
        this.origRefNo = origRefNo;
        this.authCode = authCode;
        this.origAuthCode = origAuthCode;
        this.issuerCode = issuerCode;
        this.acqCode = acqCode;
        this.hasPin = hasPin;
        this.track1 = track1;
        this.track2 = track2;
        this.track3 = track3;
        this.dupReason = dupReason;
        this.reserved = reserved;
        this.pinFree = pinFree;
        this.signFree = signFree;
        this.isCDCVM = isCDCVM;
        this.isOnlineTrans = isOnlineTrans;
        this.signData = signData;
        this.signPath = signPath;
        this.issuer_id = issuer_id;
        this.acquirer_id = acquirer_id;
        this.emvResult = emvResult;
        this.cardSerialNo = cardSerialNo;
        this.sendIccData = sendIccData;
        this.dupIccData = dupIccData;
        this.tc = tc;
        this.arqc = arqc;
        this.arpc = arpc;
        this.tvr = tvr;
        this.aid = aid;
        this.emvAppLabel = emvAppLabel;
        this.emvAppName = emvAppName;
        this.tsi = tsi;
        this.atc = atc;
        this.reversalStatus = reversalStatus;
        this.phoneNum = phoneNum;
        this.email = email;
        this.maskPan = maskPan;
        this.panBlock = panBlock;
    }


    public boolean isUpload() {
        return isUpload;
    }

    public void setUpload(boolean upload) {
        isUpload = upload;
    }

    public boolean isHasPin() {
        return hasPin;
    }

    public boolean isPinFree() {
        return pinFree;
    }

    public boolean isSignFree() {
        return signFree;
    }

    public boolean isCDCVM() {
        return isCDCVM;
    }

    public void setCDCVM(boolean CDCVM) {
        isCDCVM = CDCVM;
    }

    public boolean isOnlineTrans() {
        return isOnlineTrans;
    }

    public void setOnlineTrans(boolean onlineTrans) {
        isOnlineTrans = onlineTrans;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getTpdu() {
        return tpdu;
    }

    public void setTpdu(String tpdu) {
        this.tpdu = tpdu;
    }

    public String getField48() {
        return field48;
    }

    public void setField48(String field48) {
        this.field48 = field48;
    }

    public String getField60() {
        return field60;
    }

    public void setField60(String field60) {
        this.field60 = field60;
    }

    public String getField62() {
        return field62;
    }

    public void setField62(String field62) {
        this.field62 = field62;
    }

    public String getField63() {
        return field63;
    }

    public void setField63(String field63) {
        this.field63 = field63;
    }

    public String getRecvIccData() {
        return recvIccData;
    }

    public void setRecvIccData(String recvIccData) {
        this.recvIccData = recvIccData;
    }

    public String getField3() {
        return field3;
    }

    public void setField3(String field3) {
        this.field3 = field3;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getTraceNo() {
        return this.traceNo;
    }

    public void setTraceNo(long traceNo) {
        this.traceNo = traceNo;
    }

    public long getOrigTransNo() {
        return this.origTransNo;
    }

    public void setOrigTransNo(long origTransNo) {
        this.origTransNo = origTransNo;
    }

    public String getTransType() {
        return this.transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public String getOrigTransType() {
        return this.origTransType;
    }

    public void setOrigTransType(String origTransType) {
        this.origTransType = origTransType;
    }

    public ETransStatus getTransState() {
        return this.transState;
    }

    public void setTransState(ETransStatus transState) {
        this.transState = transState;
    }

    public boolean getIsUpload() {
        return this.isUpload;
    }

    public void setIsUpload(boolean isUpload) {
        this.isUpload = isUpload;
    }

    public OfflineStatus getOfflineSendState() {
        return this.offlineSendState;
    }

    public void setOfflineSendState(OfflineStatus offlineSendState) {
        this.offlineSendState = offlineSendState;
    }

    public int getSendTimes() {
        return this.sendTimes;
    }

    public void setSendTimes(int sendTimes) {
        this.sendTimes = sendTimes;
    }

    public String getProcCode() {
        return this.procCode;
    }

    public void setProcCode(String procCode) {
        this.procCode = procCode;
    }

    public String getAmount() {
        return this.amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getTipAmount() {
        return this.tipAmount;
    }

    public void setTipAmount(String tipAmount) {
        this.tipAmount = tipAmount;
    }

    public Locale getCurrency() {
        return this.currency;
    }

    public void setCurrency(Locale currency) {
        this.currency = currency;
    }

    public long getBatchNo() {
        return this.batchNo;
    }

    public void setBatchNo(long batchNo) {
        this.batchNo = batchNo;
    }

    public long getOrigBatchNo() {
        return this.origBatchNo;
    }

    public void setOrigBatchNo(long origBatchNo) {
        this.origBatchNo = origBatchNo;
    }

    public String getPan() {
        return this.pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getMaskPan() {
        return maskPan;
    }

    public void setMaskPan(String maskPan) {
        this.maskPan = maskPan;
    }

    public String getPanBlock() {
        return panBlock;
    }

    public void setPanBlock(String panBlock) {
        this.panBlock = panBlock;
    }

    public String getDateTime() {
        return this.dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }

    public String getOrigDateTime() {
        return this.origDateTime;
    }

    public void setOrigDateTime(String origDateTime) {
        this.origDateTime = origDateTime;
    }

    public String getSettleDateTime() {
        return this.settleDateTime;
    }

    public void setSettleDateTime(String settleDateTime) {
        this.settleDateTime = settleDateTime;
    }

    public String getExpDate() {
        return this.expDate;
    }

    public void setExpDate(String expDate) {
        this.expDate = expDate;
    }

    public EnterMode getEnterMode() {
        return this.enterMode;
    }

    public void setEnterMode(EnterMode enterMode) {
        this.enterMode = enterMode;
    }

    public String getNii() {
        return this.nii;
    }

    public void setNii(String nii) {
        this.nii = nii;
    }

    public String getRefNo() {
        return this.refNo;
    }

    public void setRefNo(String refNo) {
        this.refNo = refNo;
    }

    public String getOrigRefNo() {
        return this.origRefNo;
    }

    public void setOrigRefNo(String origRefNo) {
        this.origRefNo = origRefNo;
    }

    public String getAuthCode() {
        return this.authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getOrigAuthCode() {
        return this.origAuthCode;
    }

    public void setOrigAuthCode(String origAuthCode) {
        this.origAuthCode = origAuthCode;
    }

    public String getIssuerCode() {
        return this.issuerCode;
    }

    public void setIssuerCode(String issuerCode) {
        this.issuerCode = issuerCode;
    }

    public String getAcqCode() {
        return this.acqCode;
    }

    public void setAcqCode(String acqCode) {
        this.acqCode = acqCode;
    }

    public boolean getHasPin() {
        return this.hasPin;
    }

    public void setHasPin(boolean hasPin) {
        this.hasPin = hasPin;
    }

    public String getTrack1() {
        return this.track1;
    }

    public void setTrack1(String track1) {
        this.track1 = track1;
    }

    public String getTrack2() {
        return this.track2;
    }

    public void setTrack2(String track2) {
        this.track2 = track2;
    }

    public String getTrack3() {
        return this.track3;
    }

    public void setTrack3(String track3) {
        this.track3 = track3;
    }

    public String getDupReason() {
        return this.dupReason;
    }

    public void setDupReason(String dupReason) {
        this.dupReason = dupReason;
    }

    public String getReserved() {
        return this.reserved;
    }

    public void setReserved(String reserved) {
        this.reserved = reserved;
    }

    public boolean getPinFree() {
        return this.pinFree;
    }

    public void setPinFree(boolean pinFree) {
        this.pinFree = pinFree;
    }

    public boolean getSignFree() {
        return this.signFree;
    }

    public void setSignFree(boolean signFree) {
        this.signFree = signFree;
    }

    public boolean getIsCDCVM() {
        return this.isCDCVM;
    }

    public void setIsCDCVM(boolean isCDCVM) {
        this.isCDCVM = isCDCVM;
    }

    public boolean getIsOnlineTrans() {
        return this.isOnlineTrans;
    }

    public void setIsOnlineTrans(boolean isOnlineTrans) {
        this.isOnlineTrans = isOnlineTrans;
    }

    public byte[] getSignData() {
        return this.signData;
    }

    public void setSignData(byte[] signData) {
        this.signData = signData;
    }

    public byte[] getSignPath() {
        return this.signPath;
    }

    public void setSignPath(byte[] signPath) {
        this.signPath = signPath;
    }

    public long getIssuer_id() {
        return this.issuer_id;
    }

    public void setIssuer_id(long issuer_id) {
        this.issuer_id = issuer_id;
    }

    public long getAcquirer_id() {
        return this.acquirer_id;
    }

    public void setAcquirer_id(long acquirer_id) {
        this.acquirer_id = acquirer_id;
    }

    public String getEmvResult() {
        return this.emvResult;
    }

    public void setEmvResult(String emvResult) {
        this.emvResult = emvResult;
    }

    public String getCardSerialNo() {
        return this.cardSerialNo;
    }

    public void setCardSerialNo(String cardSerialNo) {
        this.cardSerialNo = cardSerialNo;
    }

    public String getSendIccData() {
        return this.sendIccData;
    }

    public void setSendIccData(String sendIccData) {
        this.sendIccData = sendIccData;
    }

    public String getDupIccData() {
        return this.dupIccData;
    }

    public void setDupIccData(String dupIccData) {
        this.dupIccData = dupIccData;
    }

    public String getTc() {
        return this.tc;
    }

    public void setTc(String tc) {
        this.tc = tc;
    }

    public String getArqc() {
        return this.arqc;
    }

    public void setArqc(String arqc) {
        this.arqc = arqc;
    }

    public String getArpc() {
        return this.arpc;
    }

    public void setArpc(String arpc) {
        this.arpc = arpc;
    }

    public String getTvr() {
        return this.tvr;
    }

    public void setTvr(String tvr) {
        this.tvr = tvr;
    }

    public String getAid() {
        return this.aid;
    }

    public void setAid(String aid) {
        this.aid = aid;
    }

    public String getEmvAppLabel() {
        return this.emvAppLabel;
    }

    public void setEmvAppLabel(String emvAppLabel) {
        this.emvAppLabel = emvAppLabel;
    }

    public String getEmvAppName() {
        return this.emvAppName;
    }

    public void setEmvAppName(String emvAppName) {
        this.emvAppName = emvAppName;
    }

    public String getTsi() {
        return this.tsi;
    }

    public void setTsi(String tsi) {
        this.tsi = tsi;
    }

    public String getAtc() {
        return this.atc;
    }

    public void setAtc(String atc) {
        this.atc = atc;
    }

    public ReversalStatus getReversalStatus() {
        return this.reversalStatus;
    }

    public void setReversalStatus(ReversalStatus reversalStatus) {
        this.reversalStatus = reversalStatus;
    }

    public String getPhoneNum() {
        return this.phoneNum;
    }

    public void setPhoneNum(String phoneNum) {
        this.phoneNum = phoneNum;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }



    @Nullable
    @Keep
    public Issuer getIssuer() {
        long __key = this.issuer_id;
        if (issuer__resolvedKey == null || !issuer__resolvedKey.equals(__key)) {
            DaoSession daoSession = this.daoSession;
            if (daoSession == null) {
                daoSession = DaoManager.getInstance().getDaoSession();
            }
            IssuerDao targetDao = daoSession.getIssuerDao();
            Issuer issuerNew = targetDao.load(__key);
            synchronized (this) {
                issuer = issuerNew;
                issuer__resolvedKey = __key;
            }
        }
        return issuer;
    }


    @Keep
    public Acquirer getAcquirer() {
        long __key = this.acquirer_id;
        if (acquirer__resolvedKey == null || !acquirer__resolvedKey.equals(__key)) {
            DaoSession daoSession = this.daoSession;
            if (daoSession == null) {
                daoSession = DaoManager.getInstance().getDaoSession();
            }
            AcquirerDao targetDao = daoSession.getAcquirerDao();
            Acquirer acquirerNew = targetDao.load(__key);
            synchronized (this) {
                acquirer = acquirerNew;
                acquirer__resolvedKey = __key;
            }
        }
        return acquirer;
    }





    public static class ETransStatusConverter implements PropertyConverter<ETransStatus, String> {

        @Override
        public ETransStatus convertToEntityProperty(String databaseValue) {
            if (databaseValue == null) {
                return null;
            }
            for (ETransStatus item : ETransStatus.values()) {
                if (item.toString().equals(databaseValue)) {
                    return item;
                }
            }
            return ETransStatus.NORMAL;
        }

        @Override
        public String convertToDatabaseValue(ETransStatus entityProperty) {
            return entityProperty == null ? null : entityProperty.toString();
        }
    }

    public static class OfflineStatusConverter implements PropertyConverter<OfflineStatus, String> {

        @Override
        public OfflineStatus convertToEntityProperty(String databaseValue) {
            if (databaseValue == null) {
                return null;
            }
            for (OfflineStatus item : OfflineStatus.values()) {
                if (item.name().equals(databaseValue)) {
                    return item;
                }
            }
            return null;
        }

        @Override
        public String convertToDatabaseValue(OfflineStatus entityProperty) {
            return entityProperty == null ? null : entityProperty.name();
        }
    }

    public static class LocaleConverter implements PropertyConverter<Locale, String> {

        @Override
        public Locale convertToEntityProperty(String databaseValue) {
            if (databaseValue == null || databaseValue.isEmpty()) {
                return null;
            }
            return JSON.parseObject(databaseValue, Locale.class);
        }

        @Override
        public String convertToDatabaseValue(Locale entityProperty) {
            if (entityProperty == null) {
                return "";
            }
            return JSON.toJSONString(entityProperty);
        }
    }

    public static class EnterModeConverter implements PropertyConverter<EnterMode, String> {

        @Override
        public EnterMode convertToEntityProperty(String databaseValue) {
            if (databaseValue == null) {
                return null;
            }
            for (EnterMode item : EnterMode.values()) {
                if (item.toString().equals(databaseValue)) {
                    return item;
                }
            }
            return null;
        }

        @Override
        public String convertToDatabaseValue(EnterMode entityProperty) {
            return entityProperty == null ? null : entityProperty.toString();
        }
    }


    public static class ReversalStatusConverter implements PropertyConverter<ReversalStatus, String> {

        @Override
        public ReversalStatus convertToEntityProperty(String databaseValue) {
            if (databaseValue == null) {
                return null;
            }
            for (ReversalStatus item : ReversalStatus.values()) {
                if (item.toString().equals(databaseValue)) {
                    return item;
                }
            }
            return ReversalStatus.NORMAL;
        }

        @Override
        public String convertToDatabaseValue(ReversalStatus entityProperty) {
            return entityProperty == null ? null : entityProperty.toString();
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return super.equals(obj);
    }

    /** called by internal mechanisms, do not call yourself. */
    @Generated(hash = 1331604593)
    public void setIssuer(@NotNull Issuer issuer) {
        if (issuer == null) {
            throw new DaoException(
                    "To-one property 'issuer_id' has not-null constraint; cannot set to-one to null");
        }
        synchronized (this) {
            this.issuer = issuer;
            issuer_id = issuer.getId();
            issuer__resolvedKey = issuer_id;
        }
    }

    /** called by internal mechanisms, do not call yourself. */
    @Generated(hash = 346746627)
    public void setAcquirer(@NotNull Acquirer acquirer) {
        if (acquirer == null) {
            throw new DaoException(
                    "To-one property 'acquirer_id' has not-null constraint; cannot set to-one to null");
        }
        synchronized (this) {
            this.acquirer = acquirer;
            acquirer_id = acquirer.getId();
            acquirer__resolvedKey = acquirer_id;
        }
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#delete(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 128553479)
    public void delete() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.delete(this);
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#refresh(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 1942392019)
    public void refresh() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.refresh(this);
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#update(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 713229351)
    public void update() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.update(this);
    }

    /** called by internal mechanisms, do not call yourself. */
    @Generated(hash = 718476260)
    public void __setDaoSession(DaoSession daoSession) {
        this.daoSession = daoSession;
        myDao = daoSession != null ? daoSession.getTransDataDao() : null;
    }
}
