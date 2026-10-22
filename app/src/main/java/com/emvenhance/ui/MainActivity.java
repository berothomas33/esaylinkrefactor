package com.emvenhance.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.emvenhance.BuildConfig;
import com.emvenhance.EmvEnhanceApp;
import com.emvenhance.R;
import com.emvenhance.core.param.EmvParams;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.databinding.ActivityMainBinding;

/**
 * The home screen: buttons only. Starting a transaction is delegated to
 * {@link AmountActivity}/{@link AmountFragment}; its steps, its cancel and its result live on the
 * search screen ({@link SearchCardFragment}), which holds the transaction.
 *
 * <p>The setup buttons show only what this terminal has ({@link PosTerminal#emvParams()},
 * {@link PosTerminal#countries()}); nothing here names a vendor.
 */
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        PosTerminal terminal = ((EmvEnhanceApp) getApplication()).getTerminal();

        String[] permissions = terminal.requiredPermissions();
        if (permissions.length > 0) {
            requestPermissions(permissions, PERMISSIONS_REQUEST);
        }

        binding.modeLabel.setText(getString(R.string.vendor_label, BuildConfig.VENDOR));
        binding.btnStartTransaction.setOnClickListener(
                v -> startActivity(new Intent(this, AmountActivity.class)));
        binding.btnOnboarding.setOnClickListener(
                v -> startActivity(new Intent(this, OnboardingActivity.class)));

        EmvParams params = terminal.emvParams();
        show(binding.btnShowEmvParam, params != null);
        show(binding.btnInsertEmvParam, params != null);
        if (params != null) {
            binding.btnShowEmvParam.setOnClickListener(
                    v -> startActivity(new Intent(this, EmvParamActivity.class)));
            binding.btnInsertEmvParam.setOnClickListener(v -> {
                new Thread(params::seedDefaults, "EmvParamSeed").start();
                Toast.makeText(this, "Inserting EMV param data…", Toast.LENGTH_SHORT).show();
            });
        }

        show(binding.btnChangeCountry, !terminal.countries().isEmpty());
        binding.btnChangeCountry.setOnClickListener(
                v -> startActivity(new Intent(this, CountryListActivity.class)));
    }

    private static void show(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
