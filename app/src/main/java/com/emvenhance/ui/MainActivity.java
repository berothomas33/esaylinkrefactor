package com.emvenhance.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.emvenhance.BuildConfig;
import com.emvenhance.EmvEnhanceApp;
import com.emvenhance.R;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.databinding.ActivityMainBinding;

/**
 * Observes {@link MainViewModel} only. Vendor card search is transparent via PosTerminal.
 *
 * <p>The two EMV-param debug buttons are wired by {@link VendorDebugPanel} — a flavor-provided
 * class (one implementation per pax/ingenico/fake source set) — so this class itself stays
 * vendor-agnostic.
 *
 * <p>Starting a transaction is delegated to {@link AmountActivity}/{@link AmountFragment} — the
 * old inline buttons here always used a hardcoded amount; this screen now only shows live status.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this,
                new MainViewModelFactory(((EmvEnhanceApp) getApplication()).getTerminal()))
                .get(MainViewModel.class);

        binding.modeLabel.setText(getString(R.string.vendor_label, BuildConfig.VENDOR));

        binding.btnStartTransaction.setOnClickListener(
                v -> startActivity(new Intent(this, AmountActivity.class)));
        binding.btnCancel.setOnClickListener(v -> viewModel.cancel());
        VendorDebugPanel.wire(this, binding);

        viewModel.getTransactionStep().observe(this, this::renderTransactionStep);

        viewModel.getEmvStep().observe(this, event -> {
            EmvStepEvent emvEvent = event.consume();
            if (emvEvent != null) {
                binding.stepText.setText(emvEvent.toString());
            }
        });
    }

    private void renderTransactionStep(TransactionStepEvent event) {
        TransactionStep step = event.getStep();

        boolean busy = step != TransactionStep.IDLE
                && step != TransactionStep.COMPLETED
                && step != TransactionStep.ERROR
                && step != TransactionStep.APPROVED
                && step != TransactionStep.DECLINED;
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);

        binding.panText.setText(event.getString(TransactionStepEvent.KEY_PAN));
        binding.issuerText.setText(event.getString(TransactionStepEvent.KEY_ISSUER_NAME));

        String result = event.get(TransactionStepEvent.KEY_RESULT);
        String error = event.get(TransactionStepEvent.KEY_ERROR);
        binding.resultText.setText(result != null ? result : (error != null ? error : "—"));

        switch (step) {
            case APPROVED:
                toast("Transaction approved");
                break;
            case DECLINED:
                toast("Transaction declined: " + event.getString(TransactionStepEvent.KEY_ERROR));
                break;
            case ERROR:
                toast("Error: " + event.getMessage());
                break;
            default:
                break;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
