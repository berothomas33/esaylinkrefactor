package com.pax.bizentity.entity;

import androidx.annotation.Nullable;
import com.alibaba.fastjson.JSON;
import java.io.Serializable;
import java.util.Locale;
import org.greenrobot.greendao.annotation.Convert;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.annotation.Property;
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

    public enum ETransStatus {
        NORMAL,
        VOIDED,
        ADJUSTED
    }

    public enum OfflineStatus {
        OFFLINE_NOT_SENT(0),
        OFFLINE_SENT(1),
        OFFLINE_ERR_SEND(2),
        OFFLINE_ERR_RESP(3),
        OFFLINE_ERR_UNKNOWN(0xff),;

        private final int value;

        OfflineStatus(int value) {
            this.value = value;
        }
    }

    public enum SignSendStatus {
        SEND_SIG_NO,
        SEND_SIG_SUCC,
        SEND_SIG_ERR,
    }

    public enum EnterMode {
        MANUAL("M"),
        SWIPE("S"),
        INSERT("I"),
        FALLBACK("F"),
        CLSS("C"),
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
        NORMAL,
        PENDING,
    }

    public static final String DUP_REASON_NO_RECV = "98";
    public static final String DUP_REASON_MACWRONG = "A0";
    public static final String DUP_REASON_OTHERS = "06";
    public static final String DUP_REASON_CARD_DENIED = "AA";
    public static final String DUP_REASON_RUPAY_E1 = "E1";
    public static final String DUP_REASON_RUPAY_E2 = "E2";
    public static final String DUP_REASON_RUPAY_22 = "22";

    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    protected Long id;
    @Property(nameInDb = TRACENO_FIELD_NAME)
    @Unique
    @NotNull
    protected long traceNo;
    protected long origTransNo;
    @Property(nameInDb = TYPE_FIELD_NAME)
    @NotNull
    protected String transType;
    protected String origTransType = null;
    @Property(nameInDb = STATE_FIELD_NAME)
    @NotNull
    @Convert(converter = ETransStatusConverter.class, columnType = String.class)
    protected ETransStatus transState = ETransStatus.NORMAL;
    @NotNull
    protected boolean isUpload = false;
    @Property(nameInDb = OFFLINE_STATE_FIELD_NAME)
    @Convert(converter = OfflineStatusConverter.class, columnType = String.class)
    protected OfflineStatus offlineSendState = null;
    @NotNull
    protected int sendTimes;
    protected String procCode;
    @Property(nameInDb = AMOUNT_FIELD_NAME)
    protected String amount;
    protected String tipAmount;
    @Convert(converter = LocaleConverter.class, columnType = String.class)
    protected Locale currency;
    @Property(nameInDb = BATCHNO_FIELD_NAME)
    @NotNull
    protected long batchNo;
    protected long origBatchNo;
    protected String pan;
    protected String dateTime;
    protected String origDateTime;
    protected String settleDateTime;
    protected String expDate;
    @Convert(converter = EnterModeConverter.class, columnType = String.class)
    protected EnterMode enterMode;
    protected String nii;
    protected String refNo;
    protected String origRefNo;
    protected String authCode;
    protected String origAuthCode;
    protected String issuerCode;
    protected String acqCode;
    @NotNull
    protected boolean hasPin;
    protected String track1;
    protected String track2;
    protected String track3;
    protected String dupReason;
    protected String reserved;

    @NotNull
    protected boolean pinFree = false;
    @NotNull
    protected boolean signFree = false;
    @NotNull
    protected boolean isCDCVM = false;
    @NotNull
    protected boolean isOnlineTrans = false;
    protected byte[] signData;

    @Property(nameInDb = SIGN_PATH)
    private byte[] signPath;

    private long issuerId;
    private long acquirerId;

    protected String emvResult = null;
    protected String cardSerialNo;
    protected String sendIccData;
    protected String dupIccData;
    protected String tc;
    protected String arqc;
    protected String arpc;
    protected String tvr;
    protected String aid;
    protected String emvAppLabel;
    protected String emvAppName;
    protected String tsi;
    protected String atc;

    @Property(nameInDb = REVERSAL_FIELD_NAME)
    @NotNull
    @Convert(converter = ReversalStatusConverter.class, columnType = String.class)
    protected ReversalStatus reversalStatus = ReversalStatus.NORMAL;

    private String phoneNum;

    private String email;
    protected String maskPan;
    protected String panBlock;

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

    public TransData(){

    }

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
        this.issuerId = other.issuerId;
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
            long issuerId, long acquirerId, String emvResult, String cardSerialNo, String sendIccData,
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
        this.issuerId = issuerId;
        this.acquirerId = acquirerId;
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

    public long getIssuerId() {
        return this.issuerId;
    }

    public void setIssuerId(long issuerId) {
        this.issuerId = issuerId;
    }

    public long getAcquirerId() {
        return this.acquirerId;
    }

    public void setAcquirerId(long acquirerId) {
        this.acquirerId = acquirerId;
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
}
