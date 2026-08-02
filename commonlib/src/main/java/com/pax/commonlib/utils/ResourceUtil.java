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
 * Date	                 Author	                Action
 * 20190725  	         xieYb                  Create
 * ===========================================================================================
 */

package com.pax.commonlib.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowMetrics;
import androidx.annotation.ArrayRes;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Px;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import com.pax.commonlib.application.BaseApplication;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class ResourceUtil {
    private static final String TAG = "ResourceUtil";

    private static final Resources res = BaseApplication.getAppContext().getResources();

    private ResourceUtil() {
        //do nothing
    }

    /**
     * get color value by resource id
     *
     * @param resId resource id defined in colors.xml
     * @return color value in hex
     */
    public static int getColor(@ColorRes int resId) {
        return ContextCompat.getColor(BaseApplication.getAppContext(), resId);
    }

    /**
     * get string value by resource id
     *
     * @param resId resource id defined in strings.xml
     * @return string value
     */
    public static String getString(@StringRes int resId) {
        return res.getString(resId);
    }

    /**
     * get string value by resource id
     *
     * @param resId      resource id defined in strings.xml
     * @param formatArgs the format args
     * @return string value
     */
    public static String getString(@StringRes int resId, Object... formatArgs) {
        return res.getString(resId, formatArgs);
    }

    /**
     * get Drawable by resource id
     *
     * @param resId Drawable resource id
     * @return Drawable
     */
    public static Drawable getDrawable(@DrawableRes int resId) {
        return ContextCompat.getDrawable(BaseApplication.getAppContext(), resId);
    }

    /**
     * Gets mutable list.
     *
     * @param id the id
     * @return the mutable list
     */
    public static List<String> getMutableList(@ArrayRes int id) {
        return Arrays.asList(res.getStringArray(id));
    }

    /**
     * convert dimension size to raw pixels
     *
     * @param dpValue size in dimension
     * @return size in raw pixels
     */
    public static int dp2px(float dpValue) {
        return (int) (dpValue * res.getDisplayMetrics().density + 0.5f);
    }

    /**
     * convert raw pixels size to dimension
     *
     * @param pxValue size in raw pixels
     * @return value in dimension
     */
    public static int px2dp(float pxValue) {
        return (int) (pxValue / res.getDisplayMetrics().density + 0.5f);
    }

    public static int sp2px(float spValue) {
        return (int) (spValue * res.getDisplayMetrics().scaledDensity + 0.5f);
    }

    public static int px2sp(float pxValue) {
        return (int) (pxValue / res.getDisplayMetrics().scaledDensity + 0.5f);
    }

    /**
     * get size in raw pixels by resource id defined in dimens.xml
     *
     * @param resId resource id defined in dimens.xml
     * @return size in raw pixels
     */
    @Px
    public static int getDimens(@DimenRes int resId) {
        return res.getDimensionPixelSize(resId);
    }

    /**
     * get jsonString from asset file
     *
     * @param fileName fileName in asset
     * @return jsonString
     */
    public static String getAssetFileString(String fileName) {
        StringBuilder builder = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(BaseApplication.getAppContext().getAssets().open(fileName));
             BufferedReader bufferedReader = new BufferedReader(reader);
        ) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
        } catch (IOException e) {
            LogUtils.e("",e);
        }
        return builder.toString();
    }

    public static boolean isScreenOrientationPortrait() {
        return res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    public static Bitmap getImageFromAssetsFile(String fileName){
        Bitmap image = null;
        AssetManager am = res.getAssets();
        try {
            InputStream is = am.open(fileName);
            image = BitmapFactory.decodeStream(is);
            is.close();
        } catch (IOException e) {
            LogUtils.e("",e);
        }

        return image;
    }

    /**
     * Get status bar height.
     *
     * @return Status bar height in pixels.
     */
    @Px
    public static int getStatusBarHeightPixels() {
        int height = 0;
        try {
            int resId = res.getIdentifier(
                    "status_bar_height",
                    "dimen",
                    "android");
            if (resId > 0) {
                height = res.getDimensionPixelSize(resId);
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "getStatusBarHeightPixels failed", e);
        }
        LogUtils.d(TAG, "status bar height pixels: " + height);
        return height;
    }

    @Px
    public static int getNavigationBarHeightPixels() {
        int height = 0;
        try {
            int resId = res.getIdentifier(
                    "navigation_bar_height",
                    "dimen",
                    "android");
            if (resId > 0) {
                height = res.getDimensionPixelSize(resId);
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "getNavigationBarHeightPixels failed", e);
        }
        LogUtils.d(TAG, "navigation bar height pixels: " + height);
        return height;
    }

    @Px
    public static int getDisplayHeightPixels() {
        int height;
        WindowManager windowManager = (WindowManager) BaseApplication.getAppContext()
                .getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
            height = windowMetrics.getBounds().height() - getNavigationBarHeightPixels();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            Display display = windowManager.getDefaultDisplay();
            display.getMetrics(metrics);
            height = metrics.heightPixels;
        }
        LogUtils.d(TAG, "display height pixel: " + height);
        return height;
    }

    @Px
    public static int getDisplayContentHeightPixels() {
        return getDisplayHeightPixels() - getStatusBarHeightPixels();
    }
}
