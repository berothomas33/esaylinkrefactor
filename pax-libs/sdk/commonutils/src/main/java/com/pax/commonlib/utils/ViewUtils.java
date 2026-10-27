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
 * 20210514 	        xieYb                  Create
 * ===========================================================================================
 *
 */
package com.pax.commonlib.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.view.View;
import android.widget.EditText;
import com.pax.commonlib.application.BaseApplication;
import java.lang.reflect.Method;

/**
 * The type View utils.
 */
public class ViewUtils {

    private ViewUtils() {
        //do nothing
    }

    private static Resources res;
    static {
        res = BaseApplication.getAppContext().getResources();
    }

    /**
     * 得到设备屏幕的宽度
     *
     * @param context the context
     * @return the screen width
     */
    public static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     * 得到设备屏幕的高度
     *
     * @param context the context
     * @return the screen height
     */
    public static int getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    /**
     * Is screen orientation portrait boolean.
     *
     * @param context the context
     * @return the boolean
     */
    public static boolean isScreenOrientationPortrait(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    /**
     * Dp 2 px int.
     *
     * @param dpValue the dp value
     * @return the int
     */
    public static int dp2px(float dpValue){
        return (int) (dpValue*res.getDisplayMetrics().density+0.5f);
    }

    /**
     * Px 2 dp int.
     *
     * @param pxValue the px value
     * @return the int
     */
    public static int px2dp(float pxValue){
        return (int) (pxValue/res.getDisplayMetrics().density+0.5f);
    }

    /**
     * whether show system keyboard when EditText focused
     *
     * @param et   EditText
     * @param show whether show
     */
    public static void showInput(EditText et,boolean show) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            et.setShowSoftInputOnFocus(show);
            return;
        }
        Class<EditText> cls = EditText.class;
        Method method;
        try {
            method = cls.getMethod("setShowSoftInputOnFocus", boolean.class);
            method.setAccessible(true);
            method.invoke(et, show);
        } catch (Exception e) {
            LogUtils.e("disableShowInput",e.getMessage());
        }
    }

    /**
     * try get host activity from view.
     * views hosted on floating window like dialog     and toast will sure return null.
     *
     * @param view the view
     * @return host activity; or null if not available
     */
    public static Activity getActivityFromView(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
