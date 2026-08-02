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
 * Date                  Author	                 Action
 * 20190108  	         Steven.W                Create
 * ===========================================================================================
 */
package com.pax.emvbase.utils;

import android.util.SparseIntArray;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.R;

/**
 * response error message list
 */
public class EmvResultUtils {

    private static final SparseIntArray messageMap = new SparseIntArray();
    //host approved,but card denied
    public static final int EMV_RSP_ONLINE_CARD_DENIED = -201;

    static {
        messageMap.put(0, R.string.emv_rsp_emv_ok);
        messageMap.put(-1, R.string.emv_rsp_icc_reset_err);
        messageMap.put(-2, R.string.emv_rsp_icc_cmd_err);
        messageMap.put(-3, R.string.emv_rsp_icc_block);
        messageMap.put(-4, R.string.emv_rsp_emv_rsp_err);
        messageMap.put(-5, R.string.emv_rsp_emv_app_block);
        messageMap.put(-6, R.string.emv_rsp_emv_no_app);
        messageMap.put(-7, R.string.emv_rsp_emv_user_cancel);
        messageMap.put(-8, R.string.emv_rsp_emv_time_out);
        messageMap.put(-9, R.string.emv_rsp_emv_data_err);
        messageMap.put(-10, R.string.emv_rsp_emv_not_accept);
        messageMap.put(-11, R.string.emv_rsp_emv_denial);
        messageMap.put(-12, R.string.emv_rsp_emv_key_exp);
        messageMap.put(-13, R.string.emv_rsp_emv_no_pinpad);
        messageMap.put(-14, R.string.emv_rsp_emv_no_password);
        messageMap.put(-15, R.string.emv_rsp_emv_sum_err);
        messageMap.put(-16, R.string.emv_rsp_emv_not_found);
        messageMap.put(-17, R.string.emv_rsp_emv_no_data);
        messageMap.put(-18, R.string.emv_rsp_emv_overflow);
        messageMap.put(-19, R.string.emv_rsp_no_trans_log);
        messageMap.put(-20, R.string.emv_rsp_record_notexist);
        messageMap.put(-21, R.string.emv_rsp_logitem_notexist);
        messageMap.put(-22, R.string.emv_rsp_icc_rsp_6985);
        messageMap.put(-23, R.string.emv_rsp_clss_use_contact);
        messageMap.put(-24, R.string.emv_rsp_emv_file_err);
        messageMap.put(-25, R.string.emv_rsp_clss_terminate);
        messageMap.put(-26, R.string.emv_rsp_clss_failed);
        messageMap.put(-27, R.string.emv_rsp_clss_decline);
        messageMap.put(-28, R.string.emv_rsp_clss_try_another_card);
        messageMap.put(-30, R.string.emv_rsp_emv_param_err);//both contact and contactless use this
        messageMap.put(-31, R.string.emv_rsp_clss_wave2_oversea);
        messageMap.put(-32, R.string.emv_rsp_clss_wave2_terminated);
        messageMap.put(-33, R.string.emv_rsp_clss_wave2_us_card);
        messageMap.put(-34, R.string.emv_rsp_clss_wave3_ins_card);
        messageMap.put(-35, R.string.emv_rsp_clss_reselect_app);
        messageMap.put(-36, R.string.emv_rsp_clss_card_expired);
        messageMap.put(-37, R.string.emv_rsp_emv_no_app_ppse_err);
        messageMap.put(-38, R.string.emv_rsp_clss_use_vsdc);
        messageMap.put(-39, R.string.emv_rsp_clss_cvmdecline);
        messageMap.put(-40, R.string.emv_rsp_clss_refer_consumer_device);
        messageMap.put(-41, R.string.emv_rsp_clss_last_cmd_err);
        messageMap.put(-42, R.string.emv_rsp_clss_api_order_err);
        messageMap.put(-43, R.string.emv_rsp_clss_torn_cardnum_err);
        messageMap.put(-44, R.string.emv_rsp_clss_torn_aid_err);
        messageMap.put(-45, R.string.emv_rsp_clss_torn_amt_err);
        messageMap.put(-46, R.string.emv_rsp_clss_card_expired_req_online);
        messageMap.put(-47, R.string.emv_rsp_clss_file_not_found);
        messageMap.put(-48, R.string.emv_rsp_clss_try_again);
        messageMap.put(-200, R.string.emv_rsp_clss_payment_not_accept);
        messageMap.put(EMV_RSP_ONLINE_CARD_DENIED, R.string.emv_rsp_online_card_denied);

    }

    private EmvResultUtils() {

    }

    /**
     * get response message by result code
     *
     * @param ret result code
     * @return response message
     */
    public static String getMessage(int ret) {
        String message;
        int resourceId = messageMap.get(ret, -1);
        if (resourceId == -1) {
            try {
                message =  BaseApplication.getAppContext().getString(ret);
            } catch (Exception e) {
                LogUtils.e("getMessage", "", e);
                message =  BaseApplication.getAppContext().getString(R.string.err_undefine) + "[" + ret + "]";
            }
        } else {
            message =  BaseApplication.getAppContext().getString(resourceId);
        }
        return message;
    }
}
