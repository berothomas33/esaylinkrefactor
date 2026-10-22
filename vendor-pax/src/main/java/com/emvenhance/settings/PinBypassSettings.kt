package com.emvenhance.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Whether the cardholder may skip PIN entry (PIN bypass: press Enter with no digits), set from the
 * onboarding screen and stored in SharedPreferences. Both are off unless a technician turns them
 * on.
 *
 * - **Online PIN bypass** (contact and contactless): the PIN pad accepts an empty PIN and the
 *   transaction goes online without a PIN block; the kernel records "PIN not entered" in the TVR
 *   and the issuer decides.
 * - **Offline PIN bypass** (contact): the PIN pad accepts an empty PIN and the kernel moves on to
 *   the card's next CVM.
 *
 * For contact, the selected AID must allow bypass too (its `bypassPINEntry` parameter): this
 * setting can only take bypass away, never add it to an AID that doesn't allow it.
 */
class PinBypassSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isOnlinePinBypassEnabled: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_PIN_BYPASS, false)
        set(enabled) = prefs.edit().putBoolean(KEY_ONLINE_PIN_BYPASS, enabled).apply()

    var isOfflinePinBypassEnabled: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_PIN_BYPASS, false)
        set(enabled) = prefs.edit().putBoolean(KEY_OFFLINE_PIN_BYPASS, enabled).apply()

    private companion object {
        const val PREFS_NAME = "terminal_settings"
        const val KEY_ONLINE_PIN_BYPASS = "online_pin_bypass"
        const val KEY_OFFLINE_PIN_BYPASS = "offline_pin_bypass"
    }
}
