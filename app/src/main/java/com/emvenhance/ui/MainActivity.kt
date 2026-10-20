package com.emvenhance.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.emvenhance.BuildConfig
import com.emvenhance.EmvEnhanceApp
import com.emvenhance.R
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Observes [MainViewModel] only. Vendor card search is transparent via PosTerminal.
 *
 * The two EMV-param debug buttons are wired by [VendorDebugPanel] — a flavor-provided class (one
 * implementation per pax/ingenico/fake source set) — so this class itself stays vendor-agnostic.
 *
 * Starting a transaction is delegated to [AmountActivity]/[AmountFragment] — the old inline
 * buttons here always used a hardcoded amount; this screen now only shows live status.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory((application as EmvEnhanceApp).terminal)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.modeLabel.text = getString(R.string.vendor_label, BuildConfig.VENDOR)

        binding.btnStartTransaction.setOnClickListener {
            startActivity(Intent(this, AmountActivity::class.java))
        }
        binding.btnCancel.setOnClickListener { viewModel.cancel() }
        VendorDebugPanel.wire(this, binding)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.terminalStatus.collect { renderTransactionStep(it) } }
                launch { viewModel.emvStep.collect { binding.stepText.text = it.toString() } }
            }
        }
    }

    private fun renderTransactionStep(event: TransactionStepEvent) {
        val step = event.step

        val busy = step != TransactionStep.IDLE &&
            step != TransactionStep.COMPLETED &&
            step != TransactionStep.ERROR &&
            step != TransactionStep.APPROVED &&
            step != TransactionStep.DECLINED
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE

        binding.panText.text = event.getString(TransactionStepEvent.KEY_PAN)
        binding.issuerText.text = event.getString(TransactionStepEvent.KEY_ISSUER_NAME)

        val result = event.get<Any>(TransactionStepEvent.KEY_RESULT)
        val error = event.get<Any>(TransactionStepEvent.KEY_ERROR)
        binding.resultText.text = (result ?: error ?: "—").toString()

        when (step) {
            TransactionStep.APPROVED -> toast("Transaction approved")
            TransactionStep.DECLINED ->
                toast("Transaction declined: " + event.getString(TransactionStepEvent.KEY_ERROR))
            TransactionStep.ERROR -> toast("Error: " + event.message)
            else -> Unit
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
