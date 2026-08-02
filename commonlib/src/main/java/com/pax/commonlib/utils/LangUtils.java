/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2021-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                           Author                      Action
 * 2021/07/27                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.commonlib.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.util.DisplayMetrics;
import androidx.appcompat.view.ContextThemeWrapper;
import com.pax.commonlib.R;
import com.pax.commonlib.application.BaseApplication;
import java.util.Locale;

/**
 * Language Utils
 */
public class LangUtils {
    private static final String TAG = "LangUtils";

    private LangUtils() {
        // do nothing
    }

    /**
     * Get the base context to help the application switch languages correctly.
     *
     * This method is to solve the problem that the custom language does not work in
     * AppCompatActivity, so it can only be used in the {@code attachBaseContext()} method in
     * AppCompatActivity.
     *
     * @param base Base context. Application context is not allowed here
     * @param locale Locale
     * @return The processed Context
     */
    public static Context getAttachBaseContext(Context base, Locale locale) {
        if (base == null) {
            return null;
        }
        Context context = getConfResContext(base, locale);
        final Configuration conf = context.getResources().getConfiguration();
        return new ContextThemeWrapper(context, R.style.Theme_AppCompat) {
            @Override
            public void applyOverrideConfiguration(Configuration overrideConfiguration) {
                LogUtils.d(TAG, "Apply Override Configuration");
                if (overrideConfiguration != null) {
                    overrideConfiguration.setTo(conf);
                }
                super.applyOverrideConfiguration(overrideConfiguration);
            }
        };
    }

    public static Context getConfResContext(Context context, Locale locale) {
        Resources res = context.getResources();
        final Configuration conf = res.getConfiguration();
        final DisplayMetrics dm = res.getDisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            conf.setLocale(locale);
            return context.createConfigurationContext(conf);
        } else {
            conf.setLocale(locale);
            res.updateConfiguration(conf, dm);
            return context;
        }
    }

    /**
     * Change Language
     *
     * @param context Application context
     * @param locale Locale
     */
    public static void changeLanguage(Context context, Locale locale) {
        if (context == null || locale == null) {
            return;
        }
        LogUtils.d(TAG, "Change language: \n\tLanguage: " + locale.getLanguage()
                + "\n\tCountry: " + locale.getCountry()
                + "\n\tDisplay Language: " + locale.getDisplayLanguage()
                + "\n\tDisplay Country: " + locale.getDisplayCountry());
        BaseApplication.getAppContext().runOnUiThread(() -> {
            Resources res = context.getResources();
            DisplayMetrics dm = res.getDisplayMetrics();
            Configuration conf = res.getConfiguration();
            conf.setLocale(locale);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                conf.setLocales(new LocaleList(locale));
            }
            res.updateConfiguration(conf, dm);
        });
    }
}
