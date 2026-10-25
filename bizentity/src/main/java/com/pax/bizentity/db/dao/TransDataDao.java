package com.pax.bizentity.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import com.pax.bizentity.entity.TransData.ETransStatus;
import com.pax.bizentity.entity.TransData.ETransStatusConverter;
import com.pax.bizentity.entity.TransData.EnterMode;
import com.pax.bizentity.entity.TransData.EnterModeConverter;
import com.pax.bizentity.entity.TransData.LocaleConverter;
import com.pax.bizentity.entity.TransData.OfflineStatus;
import com.pax.bizentity.entity.TransData.OfflineStatusConverter;
import com.pax.bizentity.entity.TransData.ReversalStatus;
import com.pax.bizentity.entity.TransData.ReversalStatusConverter;
import java.util.Locale;

import com.pax.bizentity.entity.TransData;

public class TransDataDao extends AbstractDao<TransData, Long> {

    public static final String TABLENAME = "trans_data";

    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "id");
        public final static Property TraceNo = new Property(1, long.class, "traceNo", false, "trace_no");
        public final static Property OrigTransNo = new Property(2, long.class, "origTransNo", false, "ORIG_TRANS_NO");
        public final static Property TransType = new Property(3, String.class, "transType", false, "type");
        public final static Property OrigTransType = new Property(4, String.class, "origTransType", false, "ORIG_TRANS_TYPE");
        public final static Property TransState = new Property(5, String.class, "transState", false, "state");
        public final static Property IsUpload = new Property(6, boolean.class, "isUpload", false, "IS_UPLOAD");
        public final static Property OfflineSendState = new Property(7, String.class, "offlineSendState", false, "offline_state");
        public final static Property SendTimes = new Property(8, int.class, "sendTimes", false, "SEND_TIMES");
        public final static Property ProcCode = new Property(9, String.class, "procCode", false, "PROC_CODE");
        public final static Property Amount = new Property(10, String.class, "amount", false, "amount");
        public final static Property TipAmount = new Property(11, String.class, "tipAmount", false, "TIP_AMOUNT");
        public final static Property Currency = new Property(12, String.class, "currency", false, "CURRENCY");
        public final static Property BatchNo = new Property(13, long.class, "batchNo", false, "batch_no");
        public final static Property OrigBatchNo = new Property(14, long.class, "origBatchNo", false, "ORIG_BATCH_NO");
        public final static Property Pan = new Property(15, String.class, "pan", false, "PAN");
        public final static Property DateTime = new Property(16, String.class, "dateTime", false, "DATE_TIME");
        public final static Property OrigDateTime = new Property(17, String.class, "origDateTime", false, "ORIG_DATE_TIME");
        public final static Property SettleDateTime = new Property(18, String.class, "settleDateTime", false, "SETTLE_DATE_TIME");
        public final static Property ExpDate = new Property(19, String.class, "expDate", false, "EXP_DATE");
        public final static Property EnterMode = new Property(20, String.class, "enterMode", false, "ENTER_MODE");
        public final static Property Nii = new Property(21, String.class, "nii", false, "NII");
        public final static Property RefNo = new Property(22, String.class, "refNo", false, "REF_NO");
        public final static Property OrigRefNo = new Property(23, String.class, "origRefNo", false, "ORIG_REF_NO");
        public final static Property AuthCode = new Property(24, String.class, "authCode", false, "AUTH_CODE");
        public final static Property OrigAuthCode = new Property(25, String.class, "origAuthCode", false, "ORIG_AUTH_CODE");
        public final static Property IssuerCode = new Property(26, String.class, "issuerCode", false, "ISSUER_CODE");
        public final static Property AcqCode = new Property(27, String.class, "acqCode", false, "ACQ_CODE");
        public final static Property HasPin = new Property(28, boolean.class, "hasPin", false, "HAS_PIN");
        public final static Property Track1 = new Property(29, String.class, "track1", false, "TRACK1");
        public final static Property Track2 = new Property(30, String.class, "track2", false, "TRACK2");
        public final static Property Track3 = new Property(31, String.class, "track3", false, "TRACK3");
        public final static Property DupReason = new Property(32, String.class, "dupReason", false, "DUP_REASON");
        public final static Property Reserved = new Property(33, String.class, "reserved", false, "RESERVED");
        public final static Property PinFree = new Property(34, boolean.class, "pinFree", false, "PIN_FREE");
        public final static Property SignFree = new Property(35, boolean.class, "signFree", false, "SIGN_FREE");
        public final static Property IsCDCVM = new Property(36, boolean.class, "isCDCVM", false, "IS_CDCVM");
        public final static Property IsOnlineTrans = new Property(37, boolean.class, "isOnlineTrans", false, "IS_ONLINE_TRANS");
        public final static Property SignData = new Property(38, byte[].class, "signData", false, "SIGN_DATA");
        public final static Property SignPath = new Property(39, byte[].class, "signPath", false, "sign_path");
        public final static Property IssuerId = new Property(40, long.class, "issuerId", false, "ISSUER_ID");
        public final static Property AcquirerId = new Property(41, long.class, "acquirerId", false, "ACQUIRER_ID");
        public final static Property EmvResult = new Property(42, String.class, "emvResult", false, "EMV_RESULT");
        public final static Property CardSerialNo = new Property(43, String.class, "cardSerialNo", false, "CARD_SERIAL_NO");
        public final static Property SendIccData = new Property(44, String.class, "sendIccData", false, "SEND_ICC_DATA");
        public final static Property DupIccData = new Property(45, String.class, "dupIccData", false, "DUP_ICC_DATA");
        public final static Property Tc = new Property(46, String.class, "tc", false, "TC");
        public final static Property Arqc = new Property(47, String.class, "arqc", false, "ARQC");
        public final static Property Arpc = new Property(48, String.class, "arpc", false, "ARPC");
        public final static Property Tvr = new Property(49, String.class, "tvr", false, "TVR");
        public final static Property Aid = new Property(50, String.class, "aid", false, "AID");
        public final static Property EmvAppLabel = new Property(51, String.class, "emvAppLabel", false, "EMV_APP_LABEL");
        public final static Property EmvAppName = new Property(52, String.class, "emvAppName", false, "EMV_APP_NAME");
        public final static Property Tsi = new Property(53, String.class, "tsi", false, "TSI");
        public final static Property Atc = new Property(54, String.class, "atc", false, "ATC");
        public final static Property ReversalStatus = new Property(55, String.class, "reversalStatus", false, "REVERSAL");
        public final static Property PhoneNum = new Property(56, String.class, "phoneNum", false, "PHONE_NUM");
        public final static Property Email = new Property(57, String.class, "email", false, "EMAIL");
        public final static Property MaskPan = new Property(58, String.class, "maskPan", false, "MASK_PAN");
        public final static Property PanBlock = new Property(59, String.class, "panBlock", false, "PAN_BLOCK");
    }

    private final ETransStatusConverter transStateConverter = new ETransStatusConverter();
    private final OfflineStatusConverter offlineSendStateConverter = new OfflineStatusConverter();
    private final LocaleConverter currencyConverter = new LocaleConverter();
    private final EnterModeConverter enterModeConverter = new EnterModeConverter();
    private final ReversalStatusConverter reversalStatusConverter = new ReversalStatusConverter();

    public TransDataDao(DaoConfig config) {
        super(config);
    }

    public TransDataDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"trans_data\" (" +
                "\"id\" INTEGER PRIMARY KEY AUTOINCREMENT ," +
                "\"trace_no\" INTEGER NOT NULL UNIQUE ," +
                "\"ORIG_TRANS_NO\" INTEGER NOT NULL ," +
                "\"type\" TEXT NOT NULL ," +
                "\"ORIG_TRANS_TYPE\" TEXT," +
                "\"state\" TEXT NOT NULL ," +
                "\"IS_UPLOAD\" INTEGER NOT NULL ," +
                "\"offline_state\" TEXT," +
                "\"SEND_TIMES\" INTEGER NOT NULL ," +
                "\"PROC_CODE\" TEXT," +
                "\"amount\" TEXT," +
                "\"TIP_AMOUNT\" TEXT," +
                "\"CURRENCY\" TEXT," +
                "\"batch_no\" INTEGER NOT NULL ," +
                "\"ORIG_BATCH_NO\" INTEGER NOT NULL ," +
                "\"PAN\" TEXT," +
                "\"DATE_TIME\" TEXT," +
                "\"ORIG_DATE_TIME\" TEXT," +
                "\"SETTLE_DATE_TIME\" TEXT," +
                "\"EXP_DATE\" TEXT," +
                "\"ENTER_MODE\" TEXT," +
                "\"NII\" TEXT," +
                "\"REF_NO\" TEXT," +
                "\"ORIG_REF_NO\" TEXT," +
                "\"AUTH_CODE\" TEXT," +
                "\"ORIG_AUTH_CODE\" TEXT," +
                "\"ISSUER_CODE\" TEXT," +
                "\"ACQ_CODE\" TEXT," +
                "\"HAS_PIN\" INTEGER NOT NULL ," +
                "\"TRACK1\" TEXT," +
                "\"TRACK2\" TEXT," +
                "\"TRACK3\" TEXT," +
                "\"DUP_REASON\" TEXT," +
                "\"RESERVED\" TEXT," +
                "\"PIN_FREE\" INTEGER NOT NULL ," +
                "\"SIGN_FREE\" INTEGER NOT NULL ," +
                "\"IS_CDCVM\" INTEGER NOT NULL ," +
                "\"IS_ONLINE_TRANS\" INTEGER NOT NULL ," +
                "\"SIGN_DATA\" BLOB," +
                "\"sign_path\" BLOB," +
                "\"ISSUER_ID\" INTEGER NOT NULL ," +
                "\"ACQUIRER_ID\" INTEGER NOT NULL ," +
                "\"EMV_RESULT\" TEXT," +
                "\"CARD_SERIAL_NO\" TEXT," +
                "\"SEND_ICC_DATA\" TEXT," +
                "\"DUP_ICC_DATA\" TEXT," +
                "\"TC\" TEXT," +
                "\"ARQC\" TEXT," +
                "\"ARPC\" TEXT," +
                "\"TVR\" TEXT," +
                "\"AID\" TEXT," +
                "\"EMV_APP_LABEL\" TEXT," +
                "\"EMV_APP_NAME\" TEXT," +
                "\"TSI\" TEXT," +
                "\"ATC\" TEXT," +
                "\"REVERSAL\" TEXT NOT NULL ," +
                "\"PHONE_NUM\" TEXT," +
                "\"EMAIL\" TEXT," +
                "\"MASK_PAN\" TEXT," +
                "\"PAN_BLOCK\" TEXT);");
    }

    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"trans_data\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, TransData entity) {
        stmt.clearBindings();

        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
        stmt.bindLong(2, entity.getTraceNo());
        stmt.bindLong(3, entity.getOrigTransNo());
        stmt.bindString(4, entity.getTransType());

        String origTransType = entity.getOrigTransType();
        if (origTransType != null) {
            stmt.bindString(5, origTransType);
        }
        stmt.bindString(6, transStateConverter.convertToDatabaseValue(entity.getTransState()));
        stmt.bindLong(7, entity.getIsUpload() ? 1L: 0L);

        OfflineStatus offlineSendState = entity.getOfflineSendState();
        if (offlineSendState != null) {
            stmt.bindString(8, offlineSendStateConverter.convertToDatabaseValue(offlineSendState));
        }
        stmt.bindLong(9, entity.getSendTimes());

        String procCode = entity.getProcCode();
        if (procCode != null) {
            stmt.bindString(10, procCode);
        }

        String amount = entity.getAmount();
        if (amount != null) {
            stmt.bindString(11, amount);
        }

        String tipAmount = entity.getTipAmount();
        if (tipAmount != null) {
            stmt.bindString(12, tipAmount);
        }

        Locale currency = entity.getCurrency();
        if (currency != null) {
            stmt.bindString(13, currencyConverter.convertToDatabaseValue(currency));
        }
        stmt.bindLong(14, entity.getBatchNo());
        stmt.bindLong(15, entity.getOrigBatchNo());

        String pan = entity.getPan();
        if (pan != null) {
            stmt.bindString(16, pan);
        }

        String dateTime = entity.getDateTime();
        if (dateTime != null) {
            stmt.bindString(17, dateTime);
        }

        String origDateTime = entity.getOrigDateTime();
        if (origDateTime != null) {
            stmt.bindString(18, origDateTime);
        }

        String settleDateTime = entity.getSettleDateTime();
        if (settleDateTime != null) {
            stmt.bindString(19, settleDateTime);
        }

        String expDate = entity.getExpDate();
        if (expDate != null) {
            stmt.bindString(20, expDate);
        }

        EnterMode enterMode = entity.getEnterMode();
        if (enterMode != null) {
            stmt.bindString(21, enterModeConverter.convertToDatabaseValue(enterMode));
        }

        String nii = entity.getNii();
        if (nii != null) {
            stmt.bindString(22, nii);
        }

        String refNo = entity.getRefNo();
        if (refNo != null) {
            stmt.bindString(23, refNo);
        }

        String origRefNo = entity.getOrigRefNo();
        if (origRefNo != null) {
            stmt.bindString(24, origRefNo);
        }

        String authCode = entity.getAuthCode();
        if (authCode != null) {
            stmt.bindString(25, authCode);
        }

        String origAuthCode = entity.getOrigAuthCode();
        if (origAuthCode != null) {
            stmt.bindString(26, origAuthCode);
        }

        String issuerCode = entity.getIssuerCode();
        if (issuerCode != null) {
            stmt.bindString(27, issuerCode);
        }

        String acqCode = entity.getAcqCode();
        if (acqCode != null) {
            stmt.bindString(28, acqCode);
        }
        stmt.bindLong(29, entity.getHasPin() ? 1L: 0L);

        String track1 = entity.getTrack1();
        if (track1 != null) {
            stmt.bindString(30, track1);
        }

        String track2 = entity.getTrack2();
        if (track2 != null) {
            stmt.bindString(31, track2);
        }

        String track3 = entity.getTrack3();
        if (track3 != null) {
            stmt.bindString(32, track3);
        }

        String dupReason = entity.getDupReason();
        if (dupReason != null) {
            stmt.bindString(33, dupReason);
        }

        String reserved = entity.getReserved();
        if (reserved != null) {
            stmt.bindString(34, reserved);
        }
        stmt.bindLong(35, entity.getPinFree() ? 1L: 0L);
        stmt.bindLong(36, entity.getSignFree() ? 1L: 0L);
        stmt.bindLong(37, entity.getIsCDCVM() ? 1L: 0L);
        stmt.bindLong(38, entity.getIsOnlineTrans() ? 1L: 0L);

        byte[] signData = entity.getSignData();
        if (signData != null) {
            stmt.bindBlob(39, signData);
        }

        byte[] signPath = entity.getSignPath();
        if (signPath != null) {
            stmt.bindBlob(40, signPath);
        }
        stmt.bindLong(41, entity.getIssuerId());
        stmt.bindLong(42, entity.getAcquirerId());

        String emvResult = entity.getEmvResult();
        if (emvResult != null) {
            stmt.bindString(43, emvResult);
        }

        String cardSerialNo = entity.getCardSerialNo();
        if (cardSerialNo != null) {
            stmt.bindString(44, cardSerialNo);
        }

        String sendIccData = entity.getSendIccData();
        if (sendIccData != null) {
            stmt.bindString(45, sendIccData);
        }

        String dupIccData = entity.getDupIccData();
        if (dupIccData != null) {
            stmt.bindString(46, dupIccData);
        }

        String tc = entity.getTc();
        if (tc != null) {
            stmt.bindString(47, tc);
        }

        String arqc = entity.getArqc();
        if (arqc != null) {
            stmt.bindString(48, arqc);
        }

        String arpc = entity.getArpc();
        if (arpc != null) {
            stmt.bindString(49, arpc);
        }

        String tvr = entity.getTvr();
        if (tvr != null) {
            stmt.bindString(50, tvr);
        }

        String aid = entity.getAid();
        if (aid != null) {
            stmt.bindString(51, aid);
        }

        String emvAppLabel = entity.getEmvAppLabel();
        if (emvAppLabel != null) {
            stmt.bindString(52, emvAppLabel);
        }

        String emvAppName = entity.getEmvAppName();
        if (emvAppName != null) {
            stmt.bindString(53, emvAppName);
        }

        String tsi = entity.getTsi();
        if (tsi != null) {
            stmt.bindString(54, tsi);
        }

        String atc = entity.getAtc();
        if (atc != null) {
            stmt.bindString(55, atc);
        }
        stmt.bindString(56, reversalStatusConverter.convertToDatabaseValue(entity.getReversalStatus()));

        String phoneNum = entity.getPhoneNum();
        if (phoneNum != null) {
            stmt.bindString(57, phoneNum);
        }

        String email = entity.getEmail();
        if (email != null) {
            stmt.bindString(58, email);
        }

        String maskPan = entity.getMaskPan();
        if (maskPan != null) {
            stmt.bindString(59, maskPan);
        }

        String panBlock = entity.getPanBlock();
        if (panBlock != null) {
            stmt.bindString(60, panBlock);
        }
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, TransData entity) {
        stmt.clearBindings();

        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
        stmt.bindLong(2, entity.getTraceNo());
        stmt.bindLong(3, entity.getOrigTransNo());
        stmt.bindString(4, entity.getTransType());

        String origTransType = entity.getOrigTransType();
        if (origTransType != null) {
            stmt.bindString(5, origTransType);
        }
        stmt.bindString(6, transStateConverter.convertToDatabaseValue(entity.getTransState()));
        stmt.bindLong(7, entity.getIsUpload() ? 1L: 0L);

        OfflineStatus offlineSendState = entity.getOfflineSendState();
        if (offlineSendState != null) {
            stmt.bindString(8, offlineSendStateConverter.convertToDatabaseValue(offlineSendState));
        }
        stmt.bindLong(9, entity.getSendTimes());

        String procCode = entity.getProcCode();
        if (procCode != null) {
            stmt.bindString(10, procCode);
        }

        String amount = entity.getAmount();
        if (amount != null) {
            stmt.bindString(11, amount);
        }

        String tipAmount = entity.getTipAmount();
        if (tipAmount != null) {
            stmt.bindString(12, tipAmount);
        }

        Locale currency = entity.getCurrency();
        if (currency != null) {
            stmt.bindString(13, currencyConverter.convertToDatabaseValue(currency));
        }
        stmt.bindLong(14, entity.getBatchNo());
        stmt.bindLong(15, entity.getOrigBatchNo());

        String pan = entity.getPan();
        if (pan != null) {
            stmt.bindString(16, pan);
        }

        String dateTime = entity.getDateTime();
        if (dateTime != null) {
            stmt.bindString(17, dateTime);
        }

        String origDateTime = entity.getOrigDateTime();
        if (origDateTime != null) {
            stmt.bindString(18, origDateTime);
        }

        String settleDateTime = entity.getSettleDateTime();
        if (settleDateTime != null) {
            stmt.bindString(19, settleDateTime);
        }

        String expDate = entity.getExpDate();
        if (expDate != null) {
            stmt.bindString(20, expDate);
        }

        EnterMode enterMode = entity.getEnterMode();
        if (enterMode != null) {
            stmt.bindString(21, enterModeConverter.convertToDatabaseValue(enterMode));
        }

        String nii = entity.getNii();
        if (nii != null) {
            stmt.bindString(22, nii);
        }

        String refNo = entity.getRefNo();
        if (refNo != null) {
            stmt.bindString(23, refNo);
        }

        String origRefNo = entity.getOrigRefNo();
        if (origRefNo != null) {
            stmt.bindString(24, origRefNo);
        }

        String authCode = entity.getAuthCode();
        if (authCode != null) {
            stmt.bindString(25, authCode);
        }

        String origAuthCode = entity.getOrigAuthCode();
        if (origAuthCode != null) {
            stmt.bindString(26, origAuthCode);
        }

        String issuerCode = entity.getIssuerCode();
        if (issuerCode != null) {
            stmt.bindString(27, issuerCode);
        }

        String acqCode = entity.getAcqCode();
        if (acqCode != null) {
            stmt.bindString(28, acqCode);
        }
        stmt.bindLong(29, entity.getHasPin() ? 1L: 0L);

        String track1 = entity.getTrack1();
        if (track1 != null) {
            stmt.bindString(30, track1);
        }

        String track2 = entity.getTrack2();
        if (track2 != null) {
            stmt.bindString(31, track2);
        }

        String track3 = entity.getTrack3();
        if (track3 != null) {
            stmt.bindString(32, track3);
        }

        String dupReason = entity.getDupReason();
        if (dupReason != null) {
            stmt.bindString(33, dupReason);
        }

        String reserved = entity.getReserved();
        if (reserved != null) {
            stmt.bindString(34, reserved);
        }
        stmt.bindLong(35, entity.getPinFree() ? 1L: 0L);
        stmt.bindLong(36, entity.getSignFree() ? 1L: 0L);
        stmt.bindLong(37, entity.getIsCDCVM() ? 1L: 0L);
        stmt.bindLong(38, entity.getIsOnlineTrans() ? 1L: 0L);

        byte[] signData = entity.getSignData();
        if (signData != null) {
            stmt.bindBlob(39, signData);
        }

        byte[] signPath = entity.getSignPath();
        if (signPath != null) {
            stmt.bindBlob(40, signPath);
        }
        stmt.bindLong(41, entity.getIssuerId());
        stmt.bindLong(42, entity.getAcquirerId());

        String emvResult = entity.getEmvResult();
        if (emvResult != null) {
            stmt.bindString(43, emvResult);
        }

        String cardSerialNo = entity.getCardSerialNo();
        if (cardSerialNo != null) {
            stmt.bindString(44, cardSerialNo);
        }

        String sendIccData = entity.getSendIccData();
        if (sendIccData != null) {
            stmt.bindString(45, sendIccData);
        }

        String dupIccData = entity.getDupIccData();
        if (dupIccData != null) {
            stmt.bindString(46, dupIccData);
        }

        String tc = entity.getTc();
        if (tc != null) {
            stmt.bindString(47, tc);
        }

        String arqc = entity.getArqc();
        if (arqc != null) {
            stmt.bindString(48, arqc);
        }

        String arpc = entity.getArpc();
        if (arpc != null) {
            stmt.bindString(49, arpc);
        }

        String tvr = entity.getTvr();
        if (tvr != null) {
            stmt.bindString(50, tvr);
        }

        String aid = entity.getAid();
        if (aid != null) {
            stmt.bindString(51, aid);
        }

        String emvAppLabel = entity.getEmvAppLabel();
        if (emvAppLabel != null) {
            stmt.bindString(52, emvAppLabel);
        }

        String emvAppName = entity.getEmvAppName();
        if (emvAppName != null) {
            stmt.bindString(53, emvAppName);
        }

        String tsi = entity.getTsi();
        if (tsi != null) {
            stmt.bindString(54, tsi);
        }

        String atc = entity.getAtc();
        if (atc != null) {
            stmt.bindString(55, atc);
        }
        stmt.bindString(56, reversalStatusConverter.convertToDatabaseValue(entity.getReversalStatus()));

        String phoneNum = entity.getPhoneNum();
        if (phoneNum != null) {
            stmt.bindString(57, phoneNum);
        }

        String email = entity.getEmail();
        if (email != null) {
            stmt.bindString(58, email);
        }

        String maskPan = entity.getMaskPan();
        if (maskPan != null) {
            stmt.bindString(59, maskPan);
        }

        String panBlock = entity.getPanBlock();
        if (panBlock != null) {
            stmt.bindString(60, panBlock);
        }
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }

    @Override
    public TransData readEntity(Cursor cursor, int offset) {
        TransData entity = new TransData(
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0),
            cursor.getLong(offset + 1),
            cursor.getLong(offset + 2),
            cursor.getString(offset + 3),
            cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4),
            transStateConverter.convertToEntityProperty(cursor.getString(offset + 5)),
            cursor.getShort(offset + 6) != 0,
            cursor.isNull(offset + 7) ? null : offlineSendStateConverter.convertToEntityProperty(cursor.getString(offset + 7)),
            cursor.getInt(offset + 8),
            cursor.isNull(offset + 9) ? null : cursor.getString(offset + 9),
            cursor.isNull(offset + 10) ? null : cursor.getString(offset + 10),
            cursor.isNull(offset + 11) ? null : cursor.getString(offset + 11),
            cursor.isNull(offset + 12) ? null : currencyConverter.convertToEntityProperty(cursor.getString(offset + 12)),
            cursor.getLong(offset + 13),
            cursor.getLong(offset + 14),
            cursor.isNull(offset + 15) ? null : cursor.getString(offset + 15),
            cursor.isNull(offset + 16) ? null : cursor.getString(offset + 16),
            cursor.isNull(offset + 17) ? null : cursor.getString(offset + 17),
            cursor.isNull(offset + 18) ? null : cursor.getString(offset + 18),
            cursor.isNull(offset + 19) ? null : cursor.getString(offset + 19),
            cursor.isNull(offset + 20) ? null : enterModeConverter.convertToEntityProperty(cursor.getString(offset + 20)),
            cursor.isNull(offset + 21) ? null : cursor.getString(offset + 21),
            cursor.isNull(offset + 22) ? null : cursor.getString(offset + 22),
            cursor.isNull(offset + 23) ? null : cursor.getString(offset + 23),
            cursor.isNull(offset + 24) ? null : cursor.getString(offset + 24),
            cursor.isNull(offset + 25) ? null : cursor.getString(offset + 25),
            cursor.isNull(offset + 26) ? null : cursor.getString(offset + 26),
            cursor.isNull(offset + 27) ? null : cursor.getString(offset + 27),
            cursor.getShort(offset + 28) != 0,
            cursor.isNull(offset + 29) ? null : cursor.getString(offset + 29),
            cursor.isNull(offset + 30) ? null : cursor.getString(offset + 30),
            cursor.isNull(offset + 31) ? null : cursor.getString(offset + 31),
            cursor.isNull(offset + 32) ? null : cursor.getString(offset + 32),
            cursor.isNull(offset + 33) ? null : cursor.getString(offset + 33),
            cursor.getShort(offset + 34) != 0,
            cursor.getShort(offset + 35) != 0,
            cursor.getShort(offset + 36) != 0,
            cursor.getShort(offset + 37) != 0,
            cursor.isNull(offset + 38) ? null : cursor.getBlob(offset + 38),
            cursor.isNull(offset + 39) ? null : cursor.getBlob(offset + 39),
            cursor.getLong(offset + 40),
            cursor.getLong(offset + 41),
            cursor.isNull(offset + 42) ? null : cursor.getString(offset + 42),
            cursor.isNull(offset + 43) ? null : cursor.getString(offset + 43),
            cursor.isNull(offset + 44) ? null : cursor.getString(offset + 44),
            cursor.isNull(offset + 45) ? null : cursor.getString(offset + 45),
            cursor.isNull(offset + 46) ? null : cursor.getString(offset + 46),
            cursor.isNull(offset + 47) ? null : cursor.getString(offset + 47),
            cursor.isNull(offset + 48) ? null : cursor.getString(offset + 48),
            cursor.isNull(offset + 49) ? null : cursor.getString(offset + 49),
            cursor.isNull(offset + 50) ? null : cursor.getString(offset + 50),
            cursor.isNull(offset + 51) ? null : cursor.getString(offset + 51),
            cursor.isNull(offset + 52) ? null : cursor.getString(offset + 52),
            cursor.isNull(offset + 53) ? null : cursor.getString(offset + 53),
            cursor.isNull(offset + 54) ? null : cursor.getString(offset + 54),
            reversalStatusConverter.convertToEntityProperty(cursor.getString(offset + 55)),
            cursor.isNull(offset + 56) ? null : cursor.getString(offset + 56),
            cursor.isNull(offset + 57) ? null : cursor.getString(offset + 57),
            cursor.isNull(offset + 58) ? null : cursor.getString(offset + 58),
            cursor.isNull(offset + 59) ? null : cursor.getString(offset + 59)
        );
        return entity;
    }

    @Override
    public void readEntity(Cursor cursor, TransData entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setTraceNo(cursor.getLong(offset + 1));
        entity.setOrigTransNo(cursor.getLong(offset + 2));
        entity.setTransType(cursor.getString(offset + 3));
        entity.setOrigTransType(cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4));
        entity.setTransState(transStateConverter.convertToEntityProperty(cursor.getString(offset + 5)));
        entity.setIsUpload(cursor.getShort(offset + 6) != 0);
        entity.setOfflineSendState(cursor.isNull(offset + 7) ? null : offlineSendStateConverter.convertToEntityProperty(cursor.getString(offset + 7)));
        entity.setSendTimes(cursor.getInt(offset + 8));
        entity.setProcCode(cursor.isNull(offset + 9) ? null : cursor.getString(offset + 9));
        entity.setAmount(cursor.isNull(offset + 10) ? null : cursor.getString(offset + 10));
        entity.setTipAmount(cursor.isNull(offset + 11) ? null : cursor.getString(offset + 11));
        entity.setCurrency(cursor.isNull(offset + 12) ? null : currencyConverter.convertToEntityProperty(cursor.getString(offset + 12)));
        entity.setBatchNo(cursor.getLong(offset + 13));
        entity.setOrigBatchNo(cursor.getLong(offset + 14));
        entity.setPan(cursor.isNull(offset + 15) ? null : cursor.getString(offset + 15));
        entity.setDateTime(cursor.isNull(offset + 16) ? null : cursor.getString(offset + 16));
        entity.setOrigDateTime(cursor.isNull(offset + 17) ? null : cursor.getString(offset + 17));
        entity.setSettleDateTime(cursor.isNull(offset + 18) ? null : cursor.getString(offset + 18));
        entity.setExpDate(cursor.isNull(offset + 19) ? null : cursor.getString(offset + 19));
        entity.setEnterMode(cursor.isNull(offset + 20) ? null : enterModeConverter.convertToEntityProperty(cursor.getString(offset + 20)));
        entity.setNii(cursor.isNull(offset + 21) ? null : cursor.getString(offset + 21));
        entity.setRefNo(cursor.isNull(offset + 22) ? null : cursor.getString(offset + 22));
        entity.setOrigRefNo(cursor.isNull(offset + 23) ? null : cursor.getString(offset + 23));
        entity.setAuthCode(cursor.isNull(offset + 24) ? null : cursor.getString(offset + 24));
        entity.setOrigAuthCode(cursor.isNull(offset + 25) ? null : cursor.getString(offset + 25));
        entity.setIssuerCode(cursor.isNull(offset + 26) ? null : cursor.getString(offset + 26));
        entity.setAcqCode(cursor.isNull(offset + 27) ? null : cursor.getString(offset + 27));
        entity.setHasPin(cursor.getShort(offset + 28) != 0);
        entity.setTrack1(cursor.isNull(offset + 29) ? null : cursor.getString(offset + 29));
        entity.setTrack2(cursor.isNull(offset + 30) ? null : cursor.getString(offset + 30));
        entity.setTrack3(cursor.isNull(offset + 31) ? null : cursor.getString(offset + 31));
        entity.setDupReason(cursor.isNull(offset + 32) ? null : cursor.getString(offset + 32));
        entity.setReserved(cursor.isNull(offset + 33) ? null : cursor.getString(offset + 33));
        entity.setPinFree(cursor.getShort(offset + 34) != 0);
        entity.setSignFree(cursor.getShort(offset + 35) != 0);
        entity.setIsCDCVM(cursor.getShort(offset + 36) != 0);
        entity.setIsOnlineTrans(cursor.getShort(offset + 37) != 0);
        entity.setSignData(cursor.isNull(offset + 38) ? null : cursor.getBlob(offset + 38));
        entity.setSignPath(cursor.isNull(offset + 39) ? null : cursor.getBlob(offset + 39));
        entity.setIssuerId(cursor.getLong(offset + 40));
        entity.setAcquirerId(cursor.getLong(offset + 41));
        entity.setEmvResult(cursor.isNull(offset + 42) ? null : cursor.getString(offset + 42));
        entity.setCardSerialNo(cursor.isNull(offset + 43) ? null : cursor.getString(offset + 43));
        entity.setSendIccData(cursor.isNull(offset + 44) ? null : cursor.getString(offset + 44));
        entity.setDupIccData(cursor.isNull(offset + 45) ? null : cursor.getString(offset + 45));
        entity.setTc(cursor.isNull(offset + 46) ? null : cursor.getString(offset + 46));
        entity.setArqc(cursor.isNull(offset + 47) ? null : cursor.getString(offset + 47));
        entity.setArpc(cursor.isNull(offset + 48) ? null : cursor.getString(offset + 48));
        entity.setTvr(cursor.isNull(offset + 49) ? null : cursor.getString(offset + 49));
        entity.setAid(cursor.isNull(offset + 50) ? null : cursor.getString(offset + 50));
        entity.setEmvAppLabel(cursor.isNull(offset + 51) ? null : cursor.getString(offset + 51));
        entity.setEmvAppName(cursor.isNull(offset + 52) ? null : cursor.getString(offset + 52));
        entity.setTsi(cursor.isNull(offset + 53) ? null : cursor.getString(offset + 53));
        entity.setAtc(cursor.isNull(offset + 54) ? null : cursor.getString(offset + 54));
        entity.setReversalStatus(reversalStatusConverter.convertToEntityProperty(cursor.getString(offset + 55)));
        entity.setPhoneNum(cursor.isNull(offset + 56) ? null : cursor.getString(offset + 56));
        entity.setEmail(cursor.isNull(offset + 57) ? null : cursor.getString(offset + 57));
        entity.setMaskPan(cursor.isNull(offset + 58) ? null : cursor.getString(offset + 58));
        entity.setPanBlock(cursor.isNull(offset + 59) ? null : cursor.getString(offset + 59));
     }

    @Override
    protected final Long updateKeyAfterInsert(TransData entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    @Override
    public Long getKey(TransData entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(TransData entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }

}
