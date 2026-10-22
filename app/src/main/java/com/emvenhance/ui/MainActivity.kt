package com.emvenhance.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.emvenhance.BuildConfig
import com.emvenhance.R
import com.emvenhance.databinding.ActivityMainBinding

/**
 * The home screen: buttons only. Starting a transaction is delegated to
 * [AmountActivity]/[AmountFragment]; its steps, its cancel and its result live on the search
 * screen ([SearchCardFragment]), which holds the transaction.
 *
 * The EMV-param debug buttons are wired by [VendorDebugPanel] — a flavor-provided class (one
 * implementation per pax/ingenico/fake source set) — so this class itself stays vendor-agnostic.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.modeLabel.text = getString(R.string.vendor_label, BuildConfig.VENDOR)
        binding.btnStartTransaction.setOnClickListener {
            startActivity(Intent(this, AmountActivity::class.java))
        }
        VendorDebugPanel.wire(this, binding)
    }
}
