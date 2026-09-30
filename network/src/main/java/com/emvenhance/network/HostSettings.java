package com.emvenhance.network;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;

/**
 * Host header values a technician can change on the terminal, stored in SharedPreferences. Each
 * getter returns the saved value when there is one and the fixed build value
 * ({@link HostAppKeys}) otherwise — nothing saved means the fixed value is used, exactly as
 * before. Edited from the onboarding screen.
 *
 * <p>Only {@code mToken} is editable today: it's the one header value that expires (see
 * {@link HostAppKeys#MTOKEN}), so it needs replacing on the device without a rebuild.
 */
public final class HostSettings {

    private static final String PREFS_NAME = "host_settings";
    private static final String KEY_MTOKEN = "mToken";

    private final SharedPreferences prefs;

    public HostSettings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** The {@code mToken} header for {@code orchestration/sale}: saved value, else the fixed one. */
    public String getMToken() {
        String saved = getSavedMToken();
        return saved != null ? saved : HostAppKeys.MTOKEN;
    }

    /** The saved {@code mToken}, or {@code null} when the fixed value is in use. */
    @Nullable
    public String getSavedMToken() {
        String saved = prefs.getString(KEY_MTOKEN, null);
        return saved == null || saved.trim().isEmpty() ? null : saved.trim();
    }

    public boolean isMTokenOverridden() {
        return getSavedMToken() != null;
    }

    /** Saves {@code mToken}; a blank value (or the fixed value itself) clears the override. */
    public void saveMToken(@Nullable String mToken) {
        String value = mToken == null ? "" : mToken.trim();
        if (value.isEmpty() || value.equals(HostAppKeys.MTOKEN)) {
            resetMToken();
            return;
        }
        prefs.edit().putString(KEY_MTOKEN, value).apply();
    }

    /** Goes back to the fixed build value. */
    public void resetMToken() {
        prefs.edit().remove(KEY_MTOKEN).apply();
    }
}
