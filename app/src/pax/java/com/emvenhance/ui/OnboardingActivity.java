package com.emvenhance.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.emvenhance.R;
import com.emvenhance.network.HostHeaders;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.network.onboarding.model.OnboardingStatusResponse;
import com.google.android.material.button.MaterialButton;
import com.pax.poslib.model.ModelInfo;

import java.util.Map;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Runs the offline onboarding cycle ({@link OnboardingClient#runOfflineCycle}: handshake →
 * onboard → confirm).
 *
 * <p>Headers now come from {@link HostHeaders#build} — {@code aggregator-app-key}/
 * {@code system-app-key} (fixed) + {@code sn} (this terminal's serial, {@link ModelInfo#getSN()})
 * + {@code lang}, confirmed against a real captured {@code foundation/onboarding/handshake}
 * request. That request had no {@code Account-Id}/token header at all, so the Account ID / Token
 * fields below no longer gate or feed the request — kept (still persisted via
 * {@link OnboardingState#saveAccountId}/{@link OnboardingState#saveToken}) only because it's not
 * yet confirmed whether something else downstream still needs them.
 *
 * <p>{@link HostHeaders}'s {@code apiKey} is still unresolved (see its own javadoc) — passed as
 * {@code ""} here, so expect this to fail authentication until that's confirmed.
 */
public class OnboardingActivity extends AppCompatActivity {

    private final CompositeDisposable disposables = new CompositeDisposable();

    private EditText accountIdInput;
    private EditText tokenInput;

    private MaterialButton btnStartOnboarding;
    private ProgressBar onboardingProgress;
    private TextView onboardingStatusText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        setTitle(R.string.label_onboarding);

        accountIdInput = findViewById(R.id.accountIdInput);
        tokenInput = findViewById(R.id.tokenInput);
        OnboardingState savedState = new OnboardingState(this);
        if (savedState.getAccountId() != null) {
            accountIdInput.setText(savedState.getAccountId());
        }
        if (savedState.getToken() != null) {
            tokenInput.setText(savedState.getToken());
        }

        btnStartOnboarding = findViewById(R.id.btnStartOnboarding);
        onboardingProgress = findViewById(R.id.onboardingProgress);
        onboardingStatusText = findViewById(R.id.onboardingStatusText);
        btnStartOnboarding.setOnClickListener(v -> startOnboarding());
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private void startOnboarding() {
        persistAccountIdAndToken();

        setOnboardingBusy(true);
        onboardingStatusText.setText(R.string.onboarding_in_progress);

        String sn = ModelInfo.getInstance().getSN();
        Map<String, String> headers = HostHeaders.build(sn, "");

        OnboardingState state = new OnboardingState(this);
        Single<OnboardingStatusResponse> cycle = new OnboardingClient().runOfflineCycle(headers, state);

        disposables.add(cycle
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        response -> {
                            setOnboardingBusy(false);
                            onboardingStatusText.setText(R.string.onboarding_succeeded);
                        },
                        throwable -> {
                            setOnboardingBusy(false);
                            onboardingStatusText.setText(
                                    getString(R.string.onboarding_failed, message(throwable)));
                        }));
    }

    /** Not used to build request headers anymore — see the class javadoc. */
    private void persistAccountIdAndToken() {
        String accountId = accountIdInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        OnboardingState state = new OnboardingState(this);
        if (!accountId.isEmpty()) {
            state.saveAccountId(accountId);
        }
        if (!token.isEmpty()) {
            state.saveToken(token);
        }
    }

    private void setOnboardingBusy(boolean busy) {
        btnStartOnboarding.setEnabled(!busy);
        onboardingProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
    }
}
