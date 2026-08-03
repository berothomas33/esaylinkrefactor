package com.emvenhance.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.emvenhance.BuildConfig;
import com.emvenhance.EmvEnhanceApp;
import com.emvenhance.R;
import com.emvenhance.core.EmvStepEvent;
import com.emvenhance.core.TransactionStep;
import com.emvenhance.core.TransactionStepEvent;
import com.emvenhance.databinding.ActivityMainBinding;

/**
 * Observes two LiveData fields from {@link MainViewModel}. No RxJava, no subjects, no
 * disposables, no engine, no callbacks. Just LiveData → UI binding.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PROC_CODE = "000000";
    private static final long AMOUNT_MINOR = 1000L;

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

        binding.btnContact.setOnClickListener(
                v -> viewModel.startContact(PROC_CODE, AMOUNT_MINOR));
        binding.btnContactless.setOnClickListener(
                v -> viewModel.startContactless(PROC_CODE, AMOUNT_MINOR));

        // ── Transaction lifecycle (sticky — survives rotation) ───────────
        viewModel.getTransactionStep().observe(this, this::renderTransactionStep);

        // ── EMV kernel progress (consume-once — not replayed) ────────────
        viewModel.getEmvStep().observe(this, event -> {
            EmvStepEvent emvEvent = event.consume();
            if (emvEvent != null) {
                binding.stepText.setText(emvEvent.toString());
            }
        });
    }

    private void renderTransactionStep(TransactionStepEvent event) {
        TransactionStep step = event.getStep();

        // Progress spinner
        boolean busy = step != TransactionStep.IDLE
                && step != TransactionStep.COMPLETED
                && step != TransactionStep.ERROR
                && step != TransactionStep.APPROVED
                && step != TransactionStep.DECLINED;
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);

        // Card details (appear once CARD_DETECTED fires)
        binding.panText.setText(event.getString(TransactionStepEvent.KEY_PAN));
        binding.issuerText.setText(event.getString(TransactionStepEvent.KEY_ISSUER_NAME));

        // Result / error
        String result = event.get(TransactionStepEvent.KEY_RESULT);
        String error  = event.get(TransactionStepEvent.KEY_ERROR);
        binding.resultText.setText(result != null ? result : (error != null ? error : "—"));

        // Toast for notable milestones
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
