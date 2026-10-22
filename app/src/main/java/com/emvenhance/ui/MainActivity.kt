package com.emvenhance.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.emvenhance.BuildConfig
import com.emvenhance.EmvEnhanceApp
import com.emvenhance.R
import com.emvenhance.databinding.ActivityMainBinding

/**
 * The home screen: buttons only. Starting a transaction is delegated to
 * [AmountActivity]/[AmountFragment]; its steps, its cancel and its result live on the search
 * screen ([SearchCardFragment]), which holds the transaction.
 *
 * The setup buttons show only what this terminal has (`PosTerminal.emvParams()`,
 * `PosTerminal.countries()`); nothing here names a vendor.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val terminal = (application as EmvEnhanceApp).terminal

        val permissions = terminal.requiredPermissions()
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions, PERMISSIONS_REQUEST)
        }

        binding.modeLabel.text = getString(R.string.vendor_label, BuildConfig.VENDOR)
        binding.btnStartTransaction.setOnClickListener {
            startActivity(Intent(this, AmountActivity::class.java))
        }
        binding.btnOnboarding.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        val params = terminal.emvParams()
        show(binding.btnShowEmvParam, params != null)
        show(binding.btnInsertEmvParam, params != null)
        if (params != null) {
            binding.btnShowEmvParam.setOnClickListener {
                startActivity(Intent(this, EmvParamActivity::class.java))
            }
            binding.btnInsertEmvParam.setOnClickListener {
                // Blocking database work: its own thread, never the main thread.
                Thread(params::seedDefaults, "EmvParamSeed").start()
                Toast.makeText(this, "Inserting EMV param data…", Toast.LENGTH_SHORT).show()
            }
        }

        show(binding.btnChangeCountry, terminal.countries().isNotEmpty())
        binding.btnChangeCountry.setOnClickListener {
            startActivity(Intent(this, CountryListActivity::class.java))
        }
    }

    private fun show(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private companion object {
        const val PERMISSIONS_REQUEST = 1
    }
}
