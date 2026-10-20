package com.emvenhance.ui

import android.view.View
import com.emvenhance.databinding.ActivityMainBinding

/** Fake flavor: no local EMV param DB to browse — hide the PAX-only debug buttons. */
internal object VendorDebugPanel {

    fun wire(activity: MainActivity, binding: ActivityMainBinding) {
        binding.btnInsertEmvParam.visibility = View.GONE
        binding.btnShowEmvParam.visibility = View.GONE
        binding.btnChangeCountry.visibility = View.GONE
    }
}
