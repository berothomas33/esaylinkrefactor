package com.emvenhance.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class PinBypassSettings {

    private static final String PREFS_NAME = "terminal_settings";
    private static final String KEY_ONLINE_PIN_BYPASS = "online_pin_bypass";
    private static final String KEY_OFFLINE_PIN_BYPASS = "offline_pin_bypass";

    private final SharedPreferences prefs;

    public PinBypassSettings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOnlinePinBypassEnabled() {
        return prefs.getBoolean(KEY_ONLINE_PIN_BYPASS, false);
    }

    public void setOnlinePinBypassEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ONLINE_PIN_BYPASS, enabled).apply();
    }

    public boolean isOfflinePinBypassEnabled() {
        return prefs.getBoolean(KEY_OFFLINE_PIN_BYPASS, false);
    }

    public void setOfflinePinBypassEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_OFFLINE_PIN_BYPASS, enabled).apply();
    }
}
