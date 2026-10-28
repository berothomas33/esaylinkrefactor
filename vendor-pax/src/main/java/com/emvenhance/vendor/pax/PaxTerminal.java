package com.emvenhance.vendor.pax;

import androidx.annotation.Nullable;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.param.EmvParams;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.network.SaleCommunicationBehavior;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.currency.CurrencyConverter;
import com.pax.commonlib.sp.SharedPrefUtil;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.export.ConfigKeyConstant;
import com.pax.configservice.impl.ConfigInit;
import com.pax.configservice.impl.ConfigParamService;
import com.pax.configservice.impl.EmvParamService;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.poslib.model.ModelInfo;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class PaxTerminal extends PosTerminal {

    private static final String TAG = "PaxTerminal";
    private static final String KEY_EMV_CONFIG_INITIALIZED = "emv_config_initialized";

    public PaxTerminal() {
        this(new PaxKernel(),
                new SaleCommunicationBehavior(BaseApplication.getAppContext(), ModelInfo.getInstance().getSN()),
                new PaxPrinter());
    }

    private PaxTerminal(PaxKernel kernel,
            CommunicationBehavior communication,
            PrinterBehavior printer) {
        super(new PaxEmvBehavior(kernel), communication, printer);
    }

    @Nullable
    EmvProcessParam kernelParams() {
        return EmvParamService.loaded();
    }

    @Override
    public String serialNumber() {
        return ModelInfo.getInstance().getSN();
    }

    @Override
    public String[] requiredPermissions() {
        return PaxHardwarePermissions.all();
    }

    @Override
    public List<String> countries() {
        List<String> countries = new ArrayList<>();
        CurrencyConverter.getSupportedLocaleList(countries);
        return countries;
    }

    @Override
    public String setCountry(String country) {
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

    @Override
    public EmvParams emvParams() {
        return new PaxEmvParams(this);
    }

    @Override
    protected void initializeVendor() {
        PaxHardwarePermissions.logGrantState();
        ensureEmvConfigInitialized();
        LogUtils.i(TAG, "PAX terminal initialized");
    }

    private void ensureEmvConfigInitialized() {
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
