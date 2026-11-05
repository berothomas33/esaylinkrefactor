package com.pax.bizentity.entity;

import com.pax.bizentity.R;
import com.pax.commonlib.application.BaseApplication;

public enum ETransType {

    ECHO("0820", "", "", "",
            BaseApplication.getAppContext().getString(R.string.trans_echo), (byte) 0x00,
            false, false, false, false, false),
    SETTLE("0500", "", "920000", "",
            BaseApplication.getAppContext().getString(R.string.trans_settle), (byte) 0x00,
            true, true, true, false, false),

    BATCH_UP("0320", "", "000001", "",
            BaseApplication.getAppContext().getString(R.string.trans_batch_up), (byte) 0x00,
            false, false, false, false, false),
    SETTLE_END("0500", "", "960000", "",
            BaseApplication.getAppContext().getString(R.string.trans_settle_end), (byte) 0x00,
            false, false, false, false, false),

    SALE("0200", "0400", "000000", "00",
            BaseApplication.getAppContext().getString(R.string.trans_sale), ETransType.READ_MODE_ALL,
            true, true, true, true, false),

    VOID("0200", "0400", "020000", "00",
            BaseApplication.getAppContext().getString(R.string.trans_void), (byte) 0x00,
            true, true, false, false, true) ,
    REFUND("0200", "0400", "200000", "00",
            BaseApplication.getAppContext().getString(R.string.trans_refund), (byte) (SearchMode.SWIPE | SearchMode.KEYIN),
            true, true, false, true, true) ,
    ADJUST("", "", "000000", "",
            BaseApplication.getAppContext().getString(R.string.trans_adjust), (byte) 0x00,
            false, false, false, false, false),
    PREAUTH("0100", "0400", "300000", "06",
            BaseApplication.getAppContext().getString(R.string.trans_preAuth), (byte) (SearchMode.SWIPE | SearchMode.INSERT | SearchMode.KEYIN),
            true, true, false, false, false),

    OFFLINE_TRANS_SEND("0220", "", "000000", "00",
            BaseApplication.getAppContext().getString(R.string.trans_offline_send), (byte) (SearchMode.SWIPE | SearchMode.KEYIN),
            true, true, true, false, false);

    private static final byte READ_MODE_ALL = (byte) (SearchMode.SWIPE | SearchMode.INSERT | SearchMode.WAVE | SearchMode.KEYIN | SearchMode.QR);
    private String msgType;
    private String dupMsgType;
    private String procCode;
    private String serviceCode;
    private String transName;
    private byte readMode;
    private boolean isDupSendAllowed;
    private boolean isScriptSendAllowed;
    private boolean isAdjustAllowed;
    private boolean isVoidAllowed;
    private boolean isSymbolNegative;

    ETransType(String msgType, String dupMsgType, String procCode, String serviceCode,
               String transName, byte readMode, boolean isDupSendAllowed, boolean isScriptSendAllowed,
               boolean isAdjustAllowed, boolean isVoidAllowed, boolean isSymbolNegative) {
        this.msgType = msgType;
        this.dupMsgType = dupMsgType;
        this.procCode = procCode;
        this.serviceCode = serviceCode;
        this.transName = transName;
        this.readMode = readMode;
        this.isDupSendAllowed = isDupSendAllowed;
        this.isScriptSendAllowed = isScriptSendAllowed;
        this.isAdjustAllowed = isAdjustAllowed;
        this.isVoidAllowed = isVoidAllowed;
        this.isSymbolNegative = isSymbolNegative;
    }

    public String getProcCode() {
        return procCode;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public boolean isDupSendAllowed() {
        return isDupSendAllowed;
    }

    public boolean isScriptSendAllowed() {
        return isScriptSendAllowed;
    }

    public boolean isAdjustAllowed() {
        return isAdjustAllowed;
    }

    public boolean isVoidAllowed() {
        return isVoidAllowed;
    }

    public boolean isSymbolNegative() {
        return isSymbolNegative;
    }

}
