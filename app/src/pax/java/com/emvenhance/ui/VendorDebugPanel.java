package com.emvenhance.ui;

import android.content.Intent;
import android.widget.Toast;
import com.emvenhance.databinding.ActivityMainBinding;
import com.pax.configservice.impl.ConfigInit;

/** PAX flavor: wires the EMV param debug buttons to the local Greendao DB. */
final class VendorDebugPanel {

    private VendorDebugPanel() {
    }

    static void wire(MainActivity activity, ActivityMainBinding binding) {
        binding.btnInsertEmvParam.setOnClickListener(v -> {
            new ConfigInit().init();
            Toast.makeText(activity, "Inserting EMV param data…", Toast.LENGTH_SHORT).show();
        });
        binding.btnShowEmvParam.setOnClickListener(
                v -> activity.startActivity(new Intent(activity, EmvParamActivity.class)));
    }
}
