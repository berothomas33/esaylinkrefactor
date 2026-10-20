package com.emvenhance.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.emvenhance.BuildConfig
import com.emvenhance.R
import com.emvenhance.network.HostHeaders
import com.emvenhance.network.onboarding.OnboardingClient
import com.emvenhance.network.onboarding.OnboardingState
import com.emvenhance.vendor.pax.PaxEmvParamUpdateService
import com.google.android.material.button.MaterialButton
import com.pax.configservice.impl.EmvParamUpdateResult
import com.pax.configservice.impl.EmvParamUpdater
import com.pax.poslib.model.ModelInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * One category at a time: tap a category button to replace the fragment container with a fresh
 * [EmvParamListFragment] for it. Switching categories drops the previous fragment, so only the
 * category currently on screen is ever loaded.
 *
 * Also the debug/setup home for downloading a fresh EMV parameter package —
 * [PaxEmvParamUpdateService.downloadAndApply] downloads and applies a fresh
 * `emv_param.emv`/`clss_param.clss` package over whatever `ConfigInit`'s bundled-JSON first-boot
 * seed (or an earlier sync) left in place; the status line below the button reflects exactly what
 * [EmvParamUpdateResult] reports — which sections applied and their row counts, or the first
 * error hit. A failed or never attempted sync leaves the bundled/previous data as-is (see
 * `EmvParamUpdater`'s doc), so the category browser below always reflects the real current state.
 *
 * Uses [HostHeaders.buildAuthenticated] for its request headers — the real, confirmed
 * post-onboarding contract (a captured `tmsFileDownload` call used it, not the pre-token
 * [HostHeaders.build]) — so onboarding must complete first; see [downloadEmvParams], which
 * refreshes the saved token first via `OnboardingClient#ensureValidAccessToken` rather than
 * handing the host whatever token onboarding first minted, however old.
 *
 * The download runs in `lifecycleScope` (cancelled when this screen is destroyed) on
 * `Dispatchers.IO`; the result is shown back on the main thread.
 */
class EmvParamActivity : AppCompatActivity() {

    private lateinit var btnDownloadEmvParams: MaterialButton
    private lateinit var paramSyncProgress: ProgressBar
    private lateinit var paramSyncStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emv_param)
        setTitle(R.string.action_show_emv_param)

        btnDownloadEmvParams = findViewById(R.id.btnDownloadEmvParams)
        paramSyncProgress = findViewById(R.id.paramSyncProgress)
        paramSyncStatusText = findViewById(R.id.paramSyncStatusText)
        btnDownloadEmvParams.setOnClickListener { downloadEmvParams() }
        // Debug builds keep the last downloaded files (EmvParamUpdater.saveForDiagnosis).
        val btnShowDownloadedXml = findViewById<View>(R.id.btnShowDownloadedXml)
        btnShowDownloadedXml.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        btnShowDownloadedXml.setOnClickListener { chooseDownloadedXml() }

        val categoryRow = findViewById<LinearLayout>(R.id.categoryRow)
        for (category in EmvParamListFragment.Category.values()) {
            val button = MaterialButton(this)
            button.text = category.label
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            params.marginEnd = dp(8)
            button.layoutParams = params
            button.setOnClickListener { showCategory(category) }
            categoryRow.addView(button)
        }

        if (savedInstanceState == null) {
            showCategory(EmvParamListFragment.Category.AID)
        }
    }

    private fun downloadEmvParams() {
        val state = OnboardingState(this)
        if (state.getAccessToken() == null) {
            paramSyncStatusText.setText(R.string.emv_param_sync_credentials_required)
            return
        }
        val sn = ModelInfo.getInstance().sn
        // Fixed vendor literal, not ModelInfo#getTerminalModel() — a real captured old-app log
        // hit foundation/tmsFileDownload/pax (200, real zip back) regardless of the terminal's own
        // model string; this flavor is always PAX, so the per-device model ("A920Pro" etc., what
        // getTerminalModel() actually returns) was the wrong value and 500'd on the host.
        val posType = "pax"

        setSyncBusy(true)
        paramSyncStatusText.setText(R.string.emv_param_sync_in_progress)

        lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    // Refresh first if the saved token is near/past expiry — buildAuthenticated
                    // used to read OnboardingState#getAccessToken() straight off, which reused a
                    // token forever and could hand the host one already expired.
                    val accessToken = OnboardingClient().ensureValidAccessToken(HostHeaders.build(sn), state)
                    PaxEmvParamUpdateService()
                        .downloadAndApply(HostHeaders.buildAuthenticated(sn, accessToken), posType)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                setSyncBusy(false)
                paramSyncStatusText.text = getString(R.string.emv_param_sync_failed, message(throwable))
                return@launch
            }
            setSyncBusy(false)
            paramSyncStatusText.text = result.summarize()
            if (result.changedDatabase()) {
                // The database changed (rows applied or removed, even if a later section
                // failed): the next transaction loads it again.
                state.requireTerminalInitialization()
                // Refresh whichever category is on screen so an applied download shows up
                // immediately instead of waiting for the next tab switch.
                showCategory(EmvParamListFragment.Category.AID)
            }
        }
    }

    private fun setSyncBusy(busy: Boolean) {
        btnDownloadEmvParams.isEnabled = !busy
        paramSyncProgress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun showCategory(category: EmvParamListFragment.Category) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, EmvParamListFragment.newInstance(category))
            .commit()
    }

    /** Which of the last download's two files to show. */
    private fun chooseDownloadedXml() {
        val files = arrayOf(EmvParamUpdater.CONTACT_FILE, EmvParamUpdater.CONTACTLESS_FILE)
        AlertDialog.Builder(this)
            .setTitle(R.string.action_show_downloaded_xml)
            .setItems(files) { _, which -> showDownloadedXml(files[which]) }
            .show()
    }

    /** The file as the host sent it, selectable, with where it is on the device. */
    private fun showDownloadedXml(name: String) {
        val dir = EmvParamUpdater.diagnosisDir()
        val file = if (dir == null) null else File(dir, name)
        val text = if (file == null || !file.isFile) {
            getString(R.string.downloaded_xml_missing, name)
        } else {
            try {
                file.absolutePath + "\n\n" + file.readText(Charsets.UTF_8)
            } catch (e: IOException) {
                file.absolutePath + "\n\n" + message(e)
            }
        }
        val view = TextView(this)
        view.text = text
        view.setTextIsSelectable(true)
        view.typeface = Typeface.MONOSPACE
        view.textSize = 11f
        view.setPadding(dp(16), dp(12), dp(16), dp(12))
        val scroll = ScrollView(this)
        scroll.addView(view)
        AlertDialog.Builder(this)
            .setTitle(name)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        fun message(throwable: Throwable): String = throwable.message ?: throwable.toString()
    }
}
