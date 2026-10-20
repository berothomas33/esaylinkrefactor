package com.emvenhance.network

import android.content.Context
import android.content.SharedPreferences

/**
 * Host header values a technician can change on the terminal, stored in SharedPreferences. Each
 * getter returns the saved value when there is one and the fixed build value ([HostAppKeys])
 * otherwise — nothing saved means the fixed value is used, exactly as before. Edited from the
 * onboarding screen.
 *
 * Only `mToken` is editable today: it's the one header value that expires (see
 * [HostAppKeys.MTOKEN]), so it needs replacing on the device without a rebuild.
 */
class HostSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The `mToken` header for `orchestration/sale`: saved value, else the fixed one. */
    fun getMToken(): String = getSavedMToken() ?: HostAppKeys.MTOKEN

    /** The saved `mToken`, or `null` when the fixed value is in use. */
    fun getSavedMToken(): String? {
        val saved = prefs.getString(KEY_MTOKEN, null)
        return if (saved == null || saved.trim().isEmpty()) null else saved.trim()
    }

    fun isMTokenOverridden(): Boolean = getSavedMToken() != null

    /** Saves `mToken`; a blank value (or the fixed value itself) clears the override. */
    fun saveMToken(mToken: String?) {
        val value = mToken?.trim() ?: ""
        if (value.isEmpty() || value == HostAppKeys.MTOKEN) {
            resetMToken()
            return
        }
        prefs.edit().putString(KEY_MTOKEN, value).apply()
    }

    /** Goes back to the fixed build value. */
    fun resetMToken() {
        prefs.edit().remove(KEY_MTOKEN).apply()
    }

    private companion object {
        const val PREFS_NAME = "host_settings"
        const val KEY_MTOKEN = "mToken"
    }
}
