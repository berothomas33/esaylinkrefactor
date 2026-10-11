package com.emvenhance.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Whether the cardholder may skip PIN entry (PIN bypass: press Enter with no digits), set from the
 * onboarding screen and stored in SharedPreferences. Both are off unless a technician turns them
 * on.
 *
 * <ul>
 *   <li><b>Online PIN bypass</b> (contact and contactless): the PIN pad accepts an empty PIN and
 *       the transaction goes online without a PIN block; the kernel records "PIN not entered" in
 *       the TVR and the issuer decides.
 *   <li><b>Offline PIN bypass</b> (contact): the PIN pad accepts an empty PIN and the kernel moves
 *       on to the card's next CVM.
 * </ul>
 *
 * For contact, the selected AID must allow bypass too (its {@code bypassPINEntry} parameter):
 * this setting can only take bypass away, never add it to an AID that doesn't allow it.
 */
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
