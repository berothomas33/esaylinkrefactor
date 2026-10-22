package com.emvenhance.ui

import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.emvenhance.EmvEnhanceApp
import com.emvenhance.R
import com.emvenhance.network.HostHeaders
import com.emvenhance.network.HostSettings
import com.emvenhance.network.onboarding.OnboardingClient
import com.emvenhance.network.onboarding.OnboardingState
import com.emvenhance.settings.PinBypassSettings
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs the offline onboarding cycle ([OnboardingClient.runOfflineCycle]: handshake → onboard →
 * confirm → createToken) — just a button, nothing else needed. A full captured onboarding run
 * confirmed every request in that cycle needs only [HostHeaders.build]'s fixed values plus this
 * terminal's own serial number (`PosTerminal.serialNumber()`); no technician-entered credentials
 * appear anywhere in it. (An earlier version of this screen had Account ID / Token fields, added
 * before that was known — removed now that they're confirmed to feed nothing real.)
 *
 * Below the button: the PIN bypass switches ([PinBypassSettings]), then the `mToken` sent on
 * `orchestration/sale`, editable — it shows the fixed build value until a new one is saved here
 * ([HostSettings]), and the saved value is what sales use from then on — and every request
 * header, read-only.
 *
 * The cycle runs in `lifecycleScope` (cancelled when this screen is destroyed) on
 * `Dispatchers.IO`.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var btnStartOnboarding: MaterialButton
    private lateinit var onboardingProgress: ProgressBar
    private lateinit var onboardingStatusText: TextView
    private lateinit var hostSettings: HostSettings
    private lateinit var mTokenInput: EditText
    private lateinit var mTokenSourceText: TextView
    private lateinit var headersText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        setTitle(R.string.label_onboarding)

        btnStartOnboarding = findViewById(R.id.btnStartOnboarding)
        onboardingProgress = findViewById(R.id.onboardingProgress)
        onboardingStatusText = findViewById(R.id.onboardingStatusText)
        btnStartOnboarding.setOnClickListener { startOnboarding() }

        val pinBypass = PinBypassSettings(this)
        val onlinePinBypass = findViewById<SwitchMaterial>(R.id.switchOnlinePinBypass)
        val offlinePinBypass = findViewById<SwitchMaterial>(R.id.switchOfflinePinBypass)
        onlinePinBypass.isChecked = pinBypass.isOnlinePinBypassEnabled
        offlinePinBypass.isChecked = pinBypass.isOfflinePinBypassEnabled
        onlinePinBypass.setOnCheckedChangeListener { _, checked -> pinBypass.isOnlinePinBypassEnabled = checked }
        offlinePinBypass.setOnCheckedChangeListener { _, checked -> pinBypass.isOfflinePinBypassEnabled = checked }

        hostSettings = HostSettings(this)
        mTokenInput = findViewById(R.id.mTokenInput)
        mTokenSourceText = findViewById(R.id.mTokenSourceText)
        headersText = findViewById(R.id.headersText)
        mTokenInput.setText(hostSettings.getMToken())
        findViewById<View>(R.id.btnSaveMToken).setOnClickListener {
            hostSettings.saveMToken(mTokenInput.text.toString())
            mTokenInput.setText(hostSettings.getMToken())
            toast(if (hostSettings.isMTokenOverridden()) R.string.mtoken_saved else R.string.mtoken_reset)
            refreshHeaderInfo()
        }
        findViewById<View>(R.id.btnResetMToken).setOnClickListener {
            hostSettings.resetMToken()
            mTokenInput.setText(hostSettings.getMToken())
            toast(R.string.mtoken_reset)
            refreshHeaderInfo()
        }
        refreshHeaderInfo()
    }

    /** Where the mToken comes from and when it expires, plus every header, as sent now. */
    private fun refreshHeaderInfo() {
        val mToken = hostSettings.getMToken()
        val source = getString(
            if (hostSettings.isMTokenOverridden()) R.string.mtoken_source_saved else R.string.mtoken_source_fixed,
        )
        val expiry = jwtExpiry(mToken)
        mTokenSourceText.text = if (expiry != null) source + "\n" + expiry else source

        val accessToken = OnboardingState(this).getAccessToken()
        val headers = HostHeaders.describeAll(serialNumber(), accessToken, mToken)
        val sb = StringBuilder()
        for ((key, value) in headers) {
            if (sb.isNotEmpty()) {
                sb.append("\n\n")
            }
            sb.append(key).append(":\n").append(value)
        }
        headersText.text = sb
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun startOnboarding() {
        setOnboardingBusy(true)
        onboardingStatusText.setText(R.string.onboarding_in_progress)

        val headers = HostHeaders.build(serialNumber())
        val state = OnboardingState(this)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { OnboardingClient().runOfflineCycle(headers, state) }
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                setOnboardingBusy(false)
                onboardingStatusText.text = getString(R.string.onboarding_failed, message(throwable))
                return@launch
            }
            setOnboardingBusy(false)
            onboardingStatusText.setText(R.string.onboarding_succeeded)
            refreshHeaderInfo()
        }
    }

    /** This terminal's serial number, which the host identifies it by. */
    private fun serialNumber(): String = (application as EmvEnhanceApp).terminal.serialNumber()

    private fun setOnboardingBusy(busy: Boolean) {
        btnStartOnboarding.isEnabled = !busy
        onboardingProgress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private companion object {
        fun message(throwable: Throwable): String = throwable.message ?: throwable.toString()

        /**
         * "Expires ..." read from a JWT's `exp` claim (the mToken is an HS256 JWT that expires
         * days after it's minted), or `null` if the value isn't a readable JWT.
         */
        fun jwtExpiry(token: String): String? {
            val parts = token.split(".")
            if (parts.size < 2) {
                return null
            }
            return try {
                val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                val exp = JSONObject(String(payload, Charsets.UTF_8)).optLong("exp", 0)
                if (exp <= 0) {
                    return null
                }
                val expiry = Date(exp * 1000L)
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(expiry)
                if (expiry.before(Date())) "EXPIRED on $time" else "Expires $time"
            } catch (e: Exception) {
                null
            }
        }
    }
}
