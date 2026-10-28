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
import com.emvenhance.EmvEnhanceApp
import com.emvenhance.R
import com.emvenhance.core.param.EmvParams
import com.emvenhance.network.onboarding.OnboardingState
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The terminal's EMV parameters ([EmvParams], from `PosTerminal.emvParams()`). One category at a
 * time, from one source: the kernel's cache (what the next transaction uses) or the database.
 * Tap a source or a category button to replace the fragment container with a fresh
 * [EmvParamListFragment] for them, so only what is on screen is ever loaded.
 *
 * Also downloads a fresh parameter package from the host ([EmvParams.download]); the status line
 * shows what it applied and removed, or the first error. A failed download leaves the previous
 * parameters as they were. The terminal must be onboarded first (an access token).
 *
 * The download runs in `lifecycleScope` (cancelled when this screen is destroyed); the result is
 * shown back on the main thread.
 */
class EmvParamActivity : AppCompatActivity() {

    private lateinit var params: EmvParams

    /** What is on screen: one category, from the cache or the database. */
    private lateinit var category: String
    private var fromCache = true
    private lateinit var btnDownloadEmvParams: MaterialButton
    private lateinit var paramSyncProgress: ProgressBar
    private lateinit var paramSyncStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emv_param)
        setTitle(R.string.action_show_emv_param)
        params = (application as EmvEnhanceApp).terminal.emvParams() ?: run {
            finish() // the home screen doesn't offer it then
            return
        }

        btnDownloadEmvParams = findViewById(R.id.btnDownloadEmvParams)
        paramSyncProgress = findViewById(R.id.paramSyncProgress)
        paramSyncStatusText = findViewById(R.id.paramSyncStatusText)
        btnDownloadEmvParams.setOnClickListener { downloadEmvParams() }
        // Debug builds keep the last downloaded files.
        val btnShowDownloadedXml = findViewById<View>(R.id.btnShowDownloadedXml)
        btnShowDownloadedXml.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        btnShowDownloadedXml.setOnClickListener { chooseDownloadedXml() }

        val categoryRow = findViewById<LinearLayout>(R.id.categoryRow)
        for (label in params.categories()) {
            val button = MaterialButton(this)
            button.text = label
            val layout = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            layout.marginEnd = dp(8)
            button.layoutParams = layout
            button.setOnClickListener { showCategory(label) }
            categoryRow.addView(button)
        }

        // The toggle restores its own checked button after a rotation; the source follows it.
        val sourceToggle = findViewById<MaterialButtonToggleGroup>(R.id.sourceToggle)
        category = savedInstanceState?.getString(STATE_CATEGORY) ?: params.categories()[0]
        fromCache = sourceToggle.checkedButtonId == R.id.btnSourceCache
        sourceToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                fromCache = checkedId == R.id.btnSourceCache
                showCategory(category)
            }
        }

        if (savedInstanceState == null) {
            showCategory(category)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (this::category.isInitialized) {
            outState.putString(STATE_CATEGORY, category)
        }
    }

    private fun downloadEmvParams() {
        if (OnboardingState(this).getAccessToken() == null) {
            paramSyncStatusText.setText(R.string.emv_param_sync_credentials_required)
            return
        }
        setSyncBusy(true)
        paramSyncStatusText.setText(R.string.emv_param_sync_in_progress)

        lifecycleScope.launch {
            val summary = try {
                params.download()
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                setSyncBusy(false)
                paramSyncStatusText.text = getString(R.string.emv_param_sync_failed, message(throwable))
                return@launch
            }
            setSyncBusy(false)
            paramSyncStatusText.text = summary
            // Show what the download changed at once, not at the next tab switch.
            showCategory(category)
        }
    }

    private fun setSyncBusy(busy: Boolean) {
        btnDownloadEmvParams.isEnabled = !busy
        paramSyncProgress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun showCategory(category: String) {
        this.category = category
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, EmvParamListFragment.newInstance(category, fromCache))
            .commit()
    }

    /** Which of the last download's two files to show. */
    private fun chooseDownloadedXml() {
        val files = params.downloadedFiles().toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.action_show_downloaded_xml)
            .setItems(files) { _, which -> showDownloadedXml(files[which]) }
            .show()
    }

    /** The file as the host sent it, selectable, with where it is on the device. */
    private fun showDownloadedXml(name: String) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                val file = params.downloadedFile(name)
                if (file == null) {
                    getString(R.string.downloaded_xml_missing, name)
                } else {
                    try {
                        file.absolutePath + "\n\n" + file.readText(Charsets.UTF_8)
                    } catch (e: IOException) {
                        file.absolutePath + "\n\n" + message(e)
                    }
                }
            }
            showText(name, text)
        }
    }

    private fun showText(name: String, text: String) {
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
        const val STATE_CATEGORY = "category"

        fun message(throwable: Throwable): String = throwable.message ?: throwable.toString()
    }
}
