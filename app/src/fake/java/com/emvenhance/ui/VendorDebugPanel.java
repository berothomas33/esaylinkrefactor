package com.emvenhance.ui;

import android.view.View;
import com.emvenhance.databinding.ActivityMainBinding;

/** Fake flavor: no local EMV param DB to browse — hide the PAX-only debug buttons. */
final class VendorDebugPanel {

    private VendorDebugPanel() {
    }

    static void wire(MainActivity activity, ActivityMainBinding binding) {
        binding.btnInsertEmvParam.setVisibility(View.GONE);
        binding.btnShowEmvParam.setVisibility(View.GONE);
    }
}
