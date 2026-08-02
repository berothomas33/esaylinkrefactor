package com.emvenhance.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.emvenhance.BuildConfig;
import com.emvenhance.EmvEnhanceApp;
import com.emvenhance.R;
import com.emvenhance.core.EmvEvent;
import com.emvenhance.core.EmvState;
import com.emvenhance.core.EmvStep;
import com.emvenhance.core.EmvTransaction;
import com.emvenhance.databinding.ActivityMainBinding;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Subscribes straight to {@link EmvTransaction}. The transaction outlives the Activity and
 * its state subject replays the latest snapshot, so rotation needs no extra plumbing.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PROC_CODE = "000000";
    private static final long AMOUNT_MINOR = 1000L;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ActivityMainBinding binding;
    private EmvTransaction transaction;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        transaction = ((EmvEnhanceApp) getApplication()).getTransaction();
        binding.modeLabel.setText(getString(R.string.vendor_label, BuildConfig.VENDOR));

        binding.btnContact.setOnClickListener(
                v -> transaction.startContact(PROC_CODE, AMOUNT_MINOR));
        binding.btnContactless.setOnClickListener(
                v -> transaction.startContactless(PROC_CODE, AMOUNT_MINOR));

        disposables.add(transaction.state()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::render));
        disposables.add(transaction.events()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handle));
    }

    private void render(EmvState state) {
        binding.progress.setVisibility(state.isProcessing() ? View.VISIBLE : View.GONE);
        binding.stepText.setText(describe(state.getStep()));
        binding.panText.setText(orDash(state.getPan()));
        binding.issuerText.setText(orDash(state.getIssuerName()));
        binding.resultText.setText(
                state.getResult() != null ? state.getResult() : orDash(state.getError()));
    }

    private void handle(EmvEvent event) {
        switch (event.getType()) {
            case CONFIRM_CARD:
                dialog("Confirm card", event.getIssuerName()
                        + "\n" + event.getCardHolderName()
                        + "\n" + event.getPan());
                break;
            case ASK_PIN:
                toast("Enter PIN (online=" + event.isOnlinePin()
                        + ", tries left " + event.getLeftTimes() + ")");
                break;
            case FAILED:
                dialog(event.getMessage(), orEmpty(event.getDetail()));
                break;
            case STEP_CHANGED:
                // The step is already rendered from the state snapshot.
                break;
            default:
                toast(event.getMessage());
                break;
        }
    }

    private static String describe(@Nullable EmvStep step) {
        return step != null ? step.getNumber() + ". " + step.getTitle() : "—";
    }

    private static String orDash(@Nullable String value) {
        return value != null ? value : "—";
    }

    private static String orEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    private void dialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }
}
