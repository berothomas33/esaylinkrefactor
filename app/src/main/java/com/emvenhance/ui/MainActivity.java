package com.emvenhance.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.emvenhance.BuildConfig;
import com.emvenhance.R;
import com.emvenhance.databinding.ActivityMainBinding;

/**
 * The home screen: buttons only. Starting a transaction is delegated to
 * {@link AmountActivity}/{@link AmountFragment}; its steps, its cancel and its result live on the
 * search screen ({@link SearchCardFragment}), which holds the transaction.
 *
 * <p>The EMV-param debug buttons are wired by {@link VendorDebugPanel} — a flavor-provided class
 * (one implementation per pax/ingenico/fake source set) — so this class itself stays
 * vendor-agnostic.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.modeLabel.setText(getString(R.string.vendor_label, BuildConfig.VENDOR));
        binding.btnStartTransaction.setOnClickListener(
                v -> startActivity(new Intent(this, AmountActivity.class)));
        VendorDebugPanel.wire(this, binding);
    }
}
