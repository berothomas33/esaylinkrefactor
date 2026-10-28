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

import com.pax.bizentity.R;
import com.pax.commonlib.application.BaseApplication;

public enum ETransType {
    /*
     * 管理类
     */

    /**
     * 回响功能
     */
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
    /************************************************
     * 交易类
     ****************************************************/

    SALE("0200", "0400", "000000", "00",
            BaseApplication.getAppContext().getString(R.string.trans_sale), ETransType.READ_MODE_ALL,
            true, true, true, true, false),

    /**********************************************************************************************************/
    VOID("0200", "0400", "020000", "00",
            BaseApplication.getAppContext().getString(R.string.trans_void), (byte) 0x00,
            true, true, false, false, true) ,
    /**********************************************************************************************************/
    //AET-103
    REFUND("0200", "0400", "200000", "00",
            BaseApplication.getAppContext().getString(R.string.trans_refund), (byte) (SearchMode.SWIPE | SearchMode.KEYIN),
            true, true, false, true, true) ,
    /**********************************************************************************************************/
    ADJUST("", "", "000000", "",
            BaseApplication.getAppContext().getString(R.string.trans_adjust), (byte) 0x00,
            false, false, false, false, false),
    /**********************************************************************************************************/
    PREAUTH("0100", "0400", "300000", "06",
            BaseApplication.getAppContext().getString(R.string.trans_preAuth), (byte) (SearchMode.SWIPE | SearchMode.INSERT | SearchMode.KEYIN),
            true, true, false, false, false),

    /***************************************************************************************************************/
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

    /**
     * @param msgType             ：消息类型码
     * @param dupMsgType          : 冲正消息类型码
     * @param procCode            : 处理码
     * @param serviceCode         ：服务码
     * @param readMode            : read mode
     * @param transName           : 交易名称
     * @param isDupSendAllowed    ：是否冲正上送
     * @param isScriptSendAllowed ：是否脚本结果上送
     * @param isAdjustAllowed     ：is allowed to adjust
     * @param isVoidAllowed       : is allowed to void
     * @param isSymbolNegative    : is symbol negative
     */
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

    /**
     * Update the {@code transName} field in the {@code ETransType} enumeration class.
     *
     * <p>
     * After changing the application language, all text displayed on the screen should be
     * displayed in the corresponding language. However, because the restart after changing the
     * language does not destroy all objects, the enumeration class will not be recreated, which
     * results in the {@code transName} field not being updated in time, and the transaction type
     * name displayed on the screen is not in the same language as other texts.
     * </p>
     * <p>
     * Through this method, you can force the update of the value of the {@code transName} field to
     * the current language.
     * </p>
     */
    public void updateTransName() {
        switch (this) {
            case ECHO:
                transName = BaseApplication.getAppContext().getString(R.string.trans_echo);
                break;
            case SETTLE:
                transName = BaseApplication.getAppContext().getString(R.string.trans_settle);
                break;
            case BATCH_UP:
                transName = BaseApplication.getAppContext().getString(R.string.trans_batch_up);
                break;
            case SETTLE_END:
                transName = BaseApplication.getAppContext().getString(R.string.trans_settle_end);
                break;
            case SALE:
                transName = BaseApplication.getAppContext().getString(R.string.trans_sale);
                break;
            case VOID:
                transName = BaseApplication.getAppContext().getString(R.string.trans_void);
                break;
            case REFUND:
                transName = BaseApplication.getAppContext().getString(R.string.trans_refund);
                break;
            case ADJUST:
                transName = BaseApplication.getAppContext().getString(R.string.trans_adjust);
                break;
            case PREAUTH:
                transName = BaseApplication.getAppContext().getString(R.string.trans_preAuth);
                break;
            case OFFLINE_TRANS_SEND:
                transName = BaseApplication.getAppContext().getString(R.string.trans_offline_send);
                break;
            default:
                break;
        }
    }

    public String getMsgType() {
        return msgType;
    }

    public String getDupMsgType() {
        return dupMsgType;
    }

    public String getProcCode() {
        return procCode;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public String getTransName() {
        return transName;
    }

    public byte getReadMode() {
        return readMode;
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