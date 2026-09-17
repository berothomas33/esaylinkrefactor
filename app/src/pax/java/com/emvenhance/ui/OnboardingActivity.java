package com.emvenhance.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.emvenhance.R;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.network.onboarding.model.OnboardingStatusResponse;
import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The one screen for entering the Account ID / Token credentials and running the offline
 * onboarding cycle ({@link OnboardingClient#runOfflineCycle}: handshake → onboard → confirm) —
 * split out from {@code EmvParamActivity}, which used to combine this with the EMV param
 * download button and category browser on one screen. This is now the single place those
 * credentials get entered: {@link #requireHeaders} persists them via
 * {@link OnboardingState#saveAccountId}/{@link OnboardingState#saveToken} on every successful
 * run, and both {@code EmvParamActivity}'s download action and
 * {@code SaleCommunicationBehavior}'s live sale calls read them back from there — neither has
 * its own credentials UI.
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
        Map<String, String> headers = requireHeaders();
        if (headers == null) {
            return;
        }

        setOnboardingBusy(true);
        onboardingStatusText.setText(R.string.onboarding_in_progress);

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

    @Nullable
    private Map<String, String> requireHeaders() {
        String accountId = accountIdInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        if (TextUtils.isEmpty(accountId) || TextUtils.isEmpty(token)) {
            onboardingStatusText.setText(R.string.onboarding_credentials_required);
            return null;
        }
        OnboardingState state = new OnboardingState(this);
        state.saveAccountId(accountId);
        state.saveToken(token);
        return buildHeaders(accountId, token);
    }

    /**
     * Placeholder header convention — {@code Account-Id} verbatim, {@code Token} as a bearer
     * {@code Authorization} header — used until the real header contract these endpoints expect
     * is known (see {@code RetrofitCommunicationBehavior}/{@code SaleCommunicationBehavior}/
     * {@code OnboardingClient}, none of which build headers themselves either — every caller
     * supplies its own).
     */
    private static Map<String, String> buildHeaders(String accountId, String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Account-Id", accountId);
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    private void setOnboardingBusy(boolean busy) {
        btnStartOnboarding.setEnabled(!busy);
        onboardingProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
    }
}
