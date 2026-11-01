package com.emvenhance.network;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;

public final class HostSettings {

    private static final String PREFS_NAME = "host_settings";
    private static final String KEY_MTOKEN = "mToken";

    private final SharedPreferences prefs;

    public HostSettings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getMToken() {
        String saved = getSavedMToken();
        return saved != null ? saved : HostAppKeys.MTOKEN;
    }

    @Nullable
    public String getSavedMToken() {
        String saved = prefs.getString(KEY_MTOKEN, null);
        return saved == null || saved.trim().isEmpty() ? null : saved.trim();
    }

    public boolean isMTokenOverridden() {
        return getSavedMToken() != null;
    }

    public void saveMToken(@Nullable String mToken) {
        String value = mToken == null ? "" : mToken.trim();
        if (value.isEmpty() || value.equals(HostAppKeys.MTOKEN)) {
            resetMToken();
            return;
        }
        prefs.edit().putString(KEY_MTOKEN, value).apply();
    }

    public void resetMToken() {
        prefs.edit().remove(KEY_MTOKEN).apply();
    }
}
