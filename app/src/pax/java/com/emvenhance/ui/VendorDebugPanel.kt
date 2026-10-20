package com.emvenhance.ui

import android.content.Intent
import android.widget.Toast
import com.emvenhance.databinding.ActivityMainBinding
import com.emvenhance.network.onboarding.OnboardingState
import com.emvenhance.vendor.pax.PaxHardwarePermissions
import com.pax.configservice.impl.ConfigInit

/** PAX flavor: wires the EMV param debug buttons to the local Greendao DB. */
internal object VendorDebugPanel {

    fun wire(activity: MainActivity, binding: ActivityMainBinding) {
        PaxHardwarePermissions.request(activity)
        binding.btnInsertEmvParam.setOnClickListener {
            ConfigInit().init()
            OnboardingState(activity).requireTerminalInitialization()
            Toast.makeText(activity, "Inserting EMV param data…", Toast.LENGTH_SHORT).show()
        }
        binding.btnShowEmvParam.setOnClickListener {
            activity.startActivity(Intent(activity, EmvParamActivity::class.java))
        }
        binding.btnChangeCountry.setOnClickListener {
            activity.startActivity(Intent(activity, CountryListActivity::class.java))
        }
        binding.btnOnboarding.setOnClickListener {
            activity.startActivity(Intent(activity, OnboardingActivity::class.java))
        }
    }
}
