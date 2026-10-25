package com.pax.bizentity.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import com.pax.bizentity.entity.clss.paypass.PayPassAidBean;

public class PayPassAidBeanDao extends AbstractDao<PayPassAidBean, Long> {

    public static final String TABLENAME = "paypass_aid";

    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "paypass_id");
        public final static Property AppName = new Property(1, String.class, "appName", false, "APP_NAME");
        public final static Property Aid = new Property(2, String.class, "aid", false, "AID");
        public final static Property SelFlag = new Property(3, byte.class, "selFlag", false, "SEL_FLAG");
        public final static Property TransLimit = new Property(4, long.class, "transLimit", false, "TRANS_LIMIT");
        public final static Property TransLimitFlag = new Property(5, byte.class, "transLimitFlag", false, "TRANS_LIMIT_FLAG");
        public final static Property CvmTransLimit = new Property(6, long.class, "cvmTransLimit", false, "CVM_TRANS_LIMIT");
        public final static Property FloorLimit = new Property(7, long.class, "floorLimit", false, "FLOOR_LIMIT");
        public final static Property FloorLimitFlag = new Property(8, byte.class, "floorLimitFlag", false, "FLOOR_LIMIT_FLAG");
        public final static Property CvmLimit = new Property(9, long.class, "cvmLimit", false, "CVM_LIMIT");
        public final static Property CvmLimitFlag = new Property(10, byte.class, "cvmLimitFlag", false, "CVM_LIMIT_FLAG");
        public final static Property TacDenial = new Property(11, String.class, "tacDenial", false, "TAC_DENIAL");
        public final static Property TacOnline = new Property(12, String.class, "tacOnline", false, "TAC_ONLINE");
        public final static Property TacDefault = new Property(13, String.class, "tacDefault", false, "TAC_DEFAULT");
        public final static Property AcquirerId = new Property(14, String.class, "acquirerId", false, "ACQUIRER_ID");
        public final static Property Version = new Property(15, String.class, "version", false, "VERSION");
        public final static Property RiskManageData = new Property(16, String.class, "riskManageData", false, "RISK_MANAGE_DATA");
        public final static Property TerminalType = new Property(17, String.class, "terminalType", false, "TERMINAL_TYPE");
        public final static Property TerminalAdditionalCapability = new Property(18, String.class, "terminalAdditionalCapability", false, "TERMINAL_ADDITIONAL_CAPABILITY");
        public final static Property KernelConfig = new Property(19, String.class, "kernelConfig", false, "KERNEL_CONFIG");
        public final static Property CardDataInput = new Property(20, String.class, "cardDataInput", false, "CARD_DATA_INPUT");
        public final static Property CvmRequired = new Property(21, String.class, "cvmRequired", false, "CVM_REQUIRED");
        public final static Property NoCvmRequired = new Property(22, String.class, "noCvmRequired", false, "NO_CVM_REQUIRED");
        public final static Property SecurityCapability = new Property(23, String.class, "securityCapability", false, "SECURITY_CAPABILITY");
        public final static Property MagVersion = new Property(24, String.class, "magVersion", false, "MAG_VERSION");
        public final static Property MagCvm = new Property(25, String.class, "magCvm", false, "MAG_CVM");
        public final static Property MagNoCvm = new Property(26, String.class, "magNoCvm", false, "MAG_NO_CVM");
        public final static Property KernelId = new Property(27, String.class, "kernelId", false, "KERNEL_ID");
        public final static Property KernelIdBytes = new Property(28, byte[].class, "kernelIdBytes", false, "KERNEL_ID_BYTES");
        public final static Property DataExchangeSupportFlag = new Property(29, byte.class, "dataExchangeSupportFlag", false, "DATA_EXCHANGE_SUPPORT_FLAG");
        public final static Property TlvParam = new Property(30, String.class, "tlvParam", false, "TLV_PARAM");
        public final static Property DefaultUDOL = new Property(31, String.class, "defaultUDOL", false, "DEFAULT_UDOL");
        public final static Property RefundVoidFloorLimit = new Property(32, long.class, "refundVoidFloorLimit", false, "REFUND_VOID_FLOOR_LIMIT");
        public final static Property RefundVoidTacDenial = new Property(33, String.class, "refundVoidTacDenial", false, "REFUND_VOID_TAC_DENIAL");
        public final static Property SupportDefaultMcTermParam = new Property(34, boolean.class, "supportDefaultMcTermParam", false, "SUPPORT_DEFAULT_MC_TERM_PARAM");
        public final static Property MaxTornNum = new Property(35, String.class, "maxTornNum", false, "MAX_TORN_NUM");
        public final static Property MaxTornLifetime = new Property(36, String.class, "maxTornLifetime", false, "MAX_TORN_LIFETIME");
        public final static Property DeviceSN = new Property(37, String.class, "deviceSN", false, "DEVICE_SN");
        public final static Property DsOperatorId = new Property(38, String.class, "dsOperatorId", false, "DS_OPERATOR_ID");
        public final static Property MobileSupport = new Property(39, String.class, "mobileSupport", false, "MOBILE_SUPPORT");
        public final static Property AccountType = new Property(40, String.class, "accountType", false, "ACCOUNT_TYPE");
    }

    public PayPassAidBeanDao(DaoConfig config) {
        super(config);
    }

    public PayPassAidBeanDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"paypass_aid\" (" +
                "\"paypass_id\" INTEGER PRIMARY KEY AUTOINCREMENT ," +
                "\"APP_NAME\" TEXT," +
                "\"AID\" TEXT," +
                "\"SEL_FLAG\" INTEGER NOT NULL ," +
                "\"TRANS_LIMIT\" INTEGER NOT NULL ," +
                "\"TRANS_LIMIT_FLAG\" INTEGER NOT NULL ," +
                "\"CVM_TRANS_LIMIT\" INTEGER NOT NULL ," +
                "\"FLOOR_LIMIT\" INTEGER NOT NULL ," +
                "\"FLOOR_LIMIT_FLAG\" INTEGER NOT NULL ," +
                "\"CVM_LIMIT\" INTEGER NOT NULL ," +
                "\"CVM_LIMIT_FLAG\" INTEGER NOT NULL ," +
                "\"TAC_DENIAL\" TEXT," +
                "\"TAC_ONLINE\" TEXT," +
                "\"TAC_DEFAULT\" TEXT," +
                "\"ACQUIRER_ID\" TEXT," +
                "\"VERSION\" TEXT," +
                "\"RISK_MANAGE_DATA\" TEXT," +
                "\"TERMINAL_TYPE\" TEXT," +
                "\"TERMINAL_ADDITIONAL_CAPABILITY\" TEXT," +
                "\"KERNEL_CONFIG\" TEXT," +
                "\"CARD_DATA_INPUT\" TEXT," +
                "\"CVM_REQUIRED\" TEXT," +
                "\"NO_CVM_REQUIRED\" TEXT," +
                "\"SECURITY_CAPABILITY\" TEXT," +
                "\"MAG_VERSION\" TEXT," +
                "\"MAG_CVM\" TEXT," +
                "\"MAG_NO_CVM\" TEXT," +
                "\"KERNEL_ID\" TEXT," +
                "\"KERNEL_ID_BYTES\" BLOB," +
                "\"DATA_EXCHANGE_SUPPORT_FLAG\" INTEGER NOT NULL ," +
                "\"TLV_PARAM\" TEXT," +
                "\"DEFAULT_UDOL\" TEXT," +
                "\"REFUND_VOID_FLOOR_LIMIT\" INTEGER NOT NULL ," +
                "\"REFUND_VOID_TAC_DENIAL\" TEXT," +
                "\"SUPPORT_DEFAULT_MC_TERM_PARAM\" INTEGER NOT NULL ," +
                "\"MAX_TORN_NUM\" TEXT," +
                "\"MAX_TORN_LIFETIME\" TEXT," +
                "\"DEVICE_SN\" TEXT," +
                "\"DS_OPERATOR_ID\" TEXT," +
                "\"MOBILE_SUPPORT\" TEXT," +
                "\"ACCOUNT_TYPE\" TEXT);");
    }

    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"paypass_aid\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, PayPassAidBean entity) {
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
        stmt.bindLong(7, entity.getCvmTransLimit());
        stmt.bindLong(8, entity.getFloorLimit());
        stmt.bindLong(9, entity.getFloorLimitFlag());
        stmt.bindLong(10, entity.getCvmLimit());
        stmt.bindLong(11, entity.getCvmLimitFlag());

        String tacDenial = entity.getTacDenial();
        if (tacDenial != null) {
            stmt.bindString(12, tacDenial);
        }

        String tacOnline = entity.getTacOnline();
        if (tacOnline != null) {
            stmt.bindString(13, tacOnline);
        }

        String tacDefault = entity.getTacDefault();
        if (tacDefault != null) {
            stmt.bindString(14, tacDefault);
        }

        String acquirerId = entity.getAcquirerId();
        if (acquirerId != null) {
            stmt.bindString(15, acquirerId);
        }

        String version = entity.getVersion();
        if (version != null) {
            stmt.bindString(16, version);
        }

        String riskManageData = entity.getRiskManageData();
        if (riskManageData != null) {
            stmt.bindString(17, riskManageData);
        }

        String terminalType = entity.getTerminalType();
        if (terminalType != null) {
            stmt.bindString(18, terminalType);
        }

        String terminalAdditionalCapability = entity.getTerminalAdditionalCapability();
        if (terminalAdditionalCapability != null) {
            stmt.bindString(19, terminalAdditionalCapability);
        }

        String kernelConfig = entity.getKernelConfig();
        if (kernelConfig != null) {
            stmt.bindString(20, kernelConfig);
        }

        String cardDataInput = entity.getCardDataInput();
        if (cardDataInput != null) {
            stmt.bindString(21, cardDataInput);
        }

        String cvmRequired = entity.getCvmRequired();
        if (cvmRequired != null) {
            stmt.bindString(22, cvmRequired);
        }

        String noCvmRequired = entity.getNoCvmRequired();
        if (noCvmRequired != null) {
            stmt.bindString(23, noCvmRequired);
        }

        String securityCapability = entity.getSecurityCapability();
        if (securityCapability != null) {
            stmt.bindString(24, securityCapability);
        }

        String magVersion = entity.getMagVersion();
        if (magVersion != null) {
            stmt.bindString(25, magVersion);
        }

        String magCvm = entity.getMagCvm();
        if (magCvm != null) {
            stmt.bindString(26, magCvm);
        }

        String magNoCvm = entity.getMagNoCvm();
        if (magNoCvm != null) {
            stmt.bindString(27, magNoCvm);
        }

        String kernelId = entity.getKernelId();
        if (kernelId != null) {
            stmt.bindString(28, kernelId);
        }

        byte[] kernelIdBytes = entity.getKernelIdBytes();
        if (kernelIdBytes != null) {
            stmt.bindBlob(29, kernelIdBytes);
        }
        stmt.bindLong(30, entity.getDataExchangeSupportFlag());

        String tlvParam = entity.getTlvParam();
        if (tlvParam != null) {
            stmt.bindString(31, tlvParam);
        }

        String defaultUDOL = entity.getDefaultUDOL();
        if (defaultUDOL != null) {
            stmt.bindString(32, defaultUDOL);
        }
        stmt.bindLong(33, entity.getRefundVoidFloorLimit());

        String refundVoidTacDenial = entity.getRefundVoidTacDenial();
        if (refundVoidTacDenial != null) {
            stmt.bindString(34, refundVoidTacDenial);
        }
        stmt.bindLong(35, entity.getSupportDefaultMcTermParam() ? 1L: 0L);

        String maxTornNum = entity.getMaxTornNum();
        if (maxTornNum != null) {
            stmt.bindString(36, maxTornNum);
        }

        String maxTornLifetime = entity.getMaxTornLifetime();
        if (maxTornLifetime != null) {
            stmt.bindString(37, maxTornLifetime);
        }

        String deviceSN = entity.getDeviceSN();
        if (deviceSN != null) {
            stmt.bindString(38, deviceSN);
        }

        String dsOperatorId = entity.getDsOperatorId();
        if (dsOperatorId != null) {
            stmt.bindString(39, dsOperatorId);
        }

        String mobileSupport = entity.getMobileSupport();
        if (mobileSupport != null) {
            stmt.bindString(40, mobileSupport);
        }

        String accountType = entity.getAccountType();
        if (accountType != null) {
            stmt.bindString(41, accountType);
        }
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, PayPassAidBean entity) {
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
        stmt.bindLong(7, entity.getCvmTransLimit());
        stmt.bindLong(8, entity.getFloorLimit());
        stmt.bindLong(9, entity.getFloorLimitFlag());
        stmt.bindLong(10, entity.getCvmLimit());
        stmt.bindLong(11, entity.getCvmLimitFlag());

        String tacDenial = entity.getTacDenial();
        if (tacDenial != null) {
            stmt.bindString(12, tacDenial);
        }

        String tacOnline = entity.getTacOnline();
        if (tacOnline != null) {
            stmt.bindString(13, tacOnline);
        }

        String tacDefault = entity.getTacDefault();
        if (tacDefault != null) {
            stmt.bindString(14, tacDefault);
        }

        String acquirerId = entity.getAcquirerId();
        if (acquirerId != null) {
            stmt.bindString(15, acquirerId);
        }

        String version = entity.getVersion();
        if (version != null) {
            stmt.bindString(16, version);
        }

        String riskManageData = entity.getRiskManageData();
        if (riskManageData != null) {
            stmt.bindString(17, riskManageData);
        }

        String terminalType = entity.getTerminalType();
        if (terminalType != null) {
            stmt.bindString(18, terminalType);
        }

        String terminalAdditionalCapability = entity.getTerminalAdditionalCapability();
        if (terminalAdditionalCapability != null) {
            stmt.bindString(19, terminalAdditionalCapability);
        }

        String kernelConfig = entity.getKernelConfig();
        if (kernelConfig != null) {
            stmt.bindString(20, kernelConfig);
        }

        String cardDataInput = entity.getCardDataInput();
        if (cardDataInput != null) {
            stmt.bindString(21, cardDataInput);
        }

        String cvmRequired = entity.getCvmRequired();
        if (cvmRequired != null) {
            stmt.bindString(22, cvmRequired);
        }

        String noCvmRequired = entity.getNoCvmRequired();
        if (noCvmRequired != null) {
            stmt.bindString(23, noCvmRequired);
        }

        String securityCapability = entity.getSecurityCapability();
        if (securityCapability != null) {
            stmt.bindString(24, securityCapability);
        }

        String magVersion = entity.getMagVersion();
        if (magVersion != null) {
            stmt.bindString(25, magVersion);
        }

        String magCvm = entity.getMagCvm();
        if (magCvm != null) {
            stmt.bindString(26, magCvm);
        }

        String magNoCvm = entity.getMagNoCvm();
        if (magNoCvm != null) {
            stmt.bindString(27, magNoCvm);
        }

        String kernelId = entity.getKernelId();
        if (kernelId != null) {
            stmt.bindString(28, kernelId);
        }

        byte[] kernelIdBytes = entity.getKernelIdBytes();
        if (kernelIdBytes != null) {
            stmt.bindBlob(29, kernelIdBytes);
        }
        stmt.bindLong(30, entity.getDataExchangeSupportFlag());

        String tlvParam = entity.getTlvParam();
        if (tlvParam != null) {
            stmt.bindString(31, tlvParam);
        }

        String defaultUDOL = entity.getDefaultUDOL();
        if (defaultUDOL != null) {
            stmt.bindString(32, defaultUDOL);
        }
        stmt.bindLong(33, entity.getRefundVoidFloorLimit());

        String refundVoidTacDenial = entity.getRefundVoidTacDenial();
        if (refundVoidTacDenial != null) {
            stmt.bindString(34, refundVoidTacDenial);
        }
        stmt.bindLong(35, entity.getSupportDefaultMcTermParam() ? 1L: 0L);

        String maxTornNum = entity.getMaxTornNum();
        if (maxTornNum != null) {
            stmt.bindString(36, maxTornNum);
        }

        String maxTornLifetime = entity.getMaxTornLifetime();
        if (maxTornLifetime != null) {
            stmt.bindString(37, maxTornLifetime);
        }

        String deviceSN = entity.getDeviceSN();
        if (deviceSN != null) {
            stmt.bindString(38, deviceSN);
        }

        String dsOperatorId = entity.getDsOperatorId();
        if (dsOperatorId != null) {
            stmt.bindString(39, dsOperatorId);
        }

        String mobileSupport = entity.getMobileSupport();
        if (mobileSupport != null) {
            stmt.bindString(40, mobileSupport);
        }

        String accountType = entity.getAccountType();
        if (accountType != null) {
            stmt.bindString(41, accountType);
        }
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }

    @Override
    public PayPassAidBean readEntity(Cursor cursor, int offset) {
        PayPassAidBean entity = new PayPassAidBean(
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0),
            cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1),
            cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2),
            (byte) cursor.getShort(offset + 3),
            cursor.getLong(offset + 4),
            (byte) cursor.getShort(offset + 5),
            cursor.getLong(offset + 6),
            cursor.getLong(offset + 7),
            (byte) cursor.getShort(offset + 8),
            cursor.getLong(offset + 9),
            (byte) cursor.getShort(offset + 10),
            cursor.isNull(offset + 11) ? null : cursor.getString(offset + 11),
            cursor.isNull(offset + 12) ? null : cursor.getString(offset + 12),
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
            cursor.isNull(offset + 24) ? null : cursor.getString(offset + 24),
            cursor.isNull(offset + 25) ? null : cursor.getString(offset + 25),
            cursor.isNull(offset + 26) ? null : cursor.getString(offset + 26),
            cursor.isNull(offset + 27) ? null : cursor.getString(offset + 27),
            cursor.isNull(offset + 28) ? null : cursor.getBlob(offset + 28),
            (byte) cursor.getShort(offset + 29),
            cursor.isNull(offset + 30) ? null : cursor.getString(offset + 30),
            cursor.isNull(offset + 31) ? null : cursor.getString(offset + 31),
            cursor.getLong(offset + 32),
            cursor.isNull(offset + 33) ? null : cursor.getString(offset + 33),
            cursor.getShort(offset + 34) != 0,
            cursor.isNull(offset + 35) ? null : cursor.getString(offset + 35),
            cursor.isNull(offset + 36) ? null : cursor.getString(offset + 36),
            cursor.isNull(offset + 37) ? null : cursor.getString(offset + 37),
            cursor.isNull(offset + 38) ? null : cursor.getString(offset + 38),
            cursor.isNull(offset + 39) ? null : cursor.getString(offset + 39),
            cursor.isNull(offset + 40) ? null : cursor.getString(offset + 40)
        );
        return entity;
    }

    @Override
    public void readEntity(Cursor cursor, PayPassAidBean entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setAppName(cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1));
        entity.setAid(cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2));
        entity.setSelFlag((byte) cursor.getShort(offset + 3));
        entity.setTransLimit(cursor.getLong(offset + 4));
        entity.setTransLimitFlag((byte) cursor.getShort(offset + 5));
        entity.setCvmTransLimit(cursor.getLong(offset + 6));
        entity.setFloorLimit(cursor.getLong(offset + 7));
        entity.setFloorLimitFlag((byte) cursor.getShort(offset + 8));
        entity.setCvmLimit(cursor.getLong(offset + 9));
        entity.setCvmLimitFlag((byte) cursor.getShort(offset + 10));
        entity.setTacDenial(cursor.isNull(offset + 11) ? null : cursor.getString(offset + 11));
        entity.setTacOnline(cursor.isNull(offset + 12) ? null : cursor.getString(offset + 12));
        entity.setTacDefault(cursor.isNull(offset + 13) ? null : cursor.getString(offset + 13));
        entity.setAcquirerId(cursor.isNull(offset + 14) ? null : cursor.getString(offset + 14));
        entity.setVersion(cursor.isNull(offset + 15) ? null : cursor.getString(offset + 15));
        entity.setRiskManageData(cursor.isNull(offset + 16) ? null : cursor.getString(offset + 16));
        entity.setTerminalType(cursor.isNull(offset + 17) ? null : cursor.getString(offset + 17));
        entity.setTerminalAdditionalCapability(cursor.isNull(offset + 18) ? null : cursor.getString(offset + 18));
        entity.setKernelConfig(cursor.isNull(offset + 19) ? null : cursor.getString(offset + 19));
        entity.setCardDataInput(cursor.isNull(offset + 20) ? null : cursor.getString(offset + 20));
        entity.setCvmRequired(cursor.isNull(offset + 21) ? null : cursor.getString(offset + 21));
        entity.setNoCvmRequired(cursor.isNull(offset + 22) ? null : cursor.getString(offset + 22));
        entity.setSecurityCapability(cursor.isNull(offset + 23) ? null : cursor.getString(offset + 23));
        entity.setMagVersion(cursor.isNull(offset + 24) ? null : cursor.getString(offset + 24));
        entity.setMagCvm(cursor.isNull(offset + 25) ? null : cursor.getString(offset + 25));
        entity.setMagNoCvm(cursor.isNull(offset + 26) ? null : cursor.getString(offset + 26));
        entity.setKernelId(cursor.isNull(offset + 27) ? null : cursor.getString(offset + 27));
        entity.setKernelIdBytes(cursor.isNull(offset + 28) ? null : cursor.getBlob(offset + 28));
        entity.setDataExchangeSupportFlag((byte) cursor.getShort(offset + 29));
        entity.setTlvParam(cursor.isNull(offset + 30) ? null : cursor.getString(offset + 30));
        entity.setDefaultUDOL(cursor.isNull(offset + 31) ? null : cursor.getString(offset + 31));
        entity.setRefundVoidFloorLimit(cursor.getLong(offset + 32));
        entity.setRefundVoidTacDenial(cursor.isNull(offset + 33) ? null : cursor.getString(offset + 33));
        entity.setSupportDefaultMcTermParam(cursor.getShort(offset + 34) != 0);
        entity.setMaxTornNum(cursor.isNull(offset + 35) ? null : cursor.getString(offset + 35));
        entity.setMaxTornLifetime(cursor.isNull(offset + 36) ? null : cursor.getString(offset + 36));
        entity.setDeviceSN(cursor.isNull(offset + 37) ? null : cursor.getString(offset + 37));
        entity.setDsOperatorId(cursor.isNull(offset + 38) ? null : cursor.getString(offset + 38));
        entity.setMobileSupport(cursor.isNull(offset + 39) ? null : cursor.getString(offset + 39));
        entity.setAccountType(cursor.isNull(offset + 40) ? null : cursor.getString(offset + 40));
     }

    @Override
    protected final Long updateKeyAfterInsert(PayPassAidBean entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    @Override
    public Long getKey(PayPassAidBean entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(PayPassAidBean entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }

}
