package com.emvenhance.vendor.pax;

import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.currency.CurrencyConverter;
import com.pax.commonlib.sp.SharedPrefUtil;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.export.ConfigKeyConstant;
import com.pax.configservice.impl.ConfigInit;
import com.pax.configservice.impl.ConfigParamService;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

final class PaxSetup {

    private static final String TAG = "PaxSetup";
    private static final String KEY_EMV_CONFIG_INITIALIZED = "emv_config_initialized";

    private PaxSetup() { }

    static List<String> countries() {
        List<String> countries = new ArrayList<>();
        CurrencyConverter.getSupportedLocaleList(countries);
        return countries;
    }

    static String setCountry(String country) {
        new ConfigParamService().putString(ConfigKeyConstant.EDC_CURRENCY_LIST, country);
        CurrencyConverter.setDefCurrency(country);
        for (Locale locale : CurrencyConverter.getSupportedLocale()) {
            if (locale.getDisplayName(Locale.US).equals(country)) {
                try {
                    return Currency.getInstance(locale).getCurrencyCode();
                } catch (IllegalArgumentException e) {
                    return "?";
                }
            }
        }
        return "?";
    }

    static void ensureEmvConfigInitialized() {
        SharedPrefUtil prefs = new SharedPrefUtil(BaseApplication.getAppContext());
        if (prefs.getBoolean(KEY_EMV_CONFIG_INITIALIZED)) {
            LogUtils.i(TAG, "EMV config already initialized, skipping");
            return;
        }
        new Thread(() -> {
            new ConfigInit().init();
            prefs.putBoolean(KEY_EMV_CONFIG_INITIALIZED, true);
        }, "ConfigInit").start();
    }
}
