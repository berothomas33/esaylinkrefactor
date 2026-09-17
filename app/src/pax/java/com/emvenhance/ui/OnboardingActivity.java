package com.emvenhance.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.emvenhance.R;
import com.emvenhance.network.HostHeaders;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.network.onboarding.model.CreateTokenResult;
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
 * onboard → confirm → createToken) — just a button, nothing else needed. A full captured
 * onboarding run confirmed every request in that cycle needs only {@link HostHeaders#build}'s
 * fixed values plus this terminal's own serial number; no technician-entered credentials appear
 * anywhere in it. (An earlier version of this screen had Account ID / Token fields, added before
 * that was known — removed now that they're confirmed to feed nothing real.)
 */
public class OnboardingActivity extends AppCompatActivity {

    private final CompositeDisposable disposables = new CompositeDisposable();

    private MaterialButton btnStartOnboarding;
    private ProgressBar onboardingProgress;
    private TextView onboardingStatusText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        setTitle(R.string.label_onboarding);

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
        setOnboardingBusy(true);
        onboardingStatusText.setText(R.string.onboarding_in_progress);

        String sn = ModelInfo.getInstance().getSN();
        Map<String, String> headers = HostHeaders.build(sn);

        OnboardingState state = new OnboardingState(this);
        Single<CreateTokenResult> cycle = new OnboardingClient().runOfflineCycle(headers, state);

        disposables.add(cycle
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                            setOnboardingBusy(false);
                            onboardingStatusText.setText(R.string.onboarding_succeeded);
                        },
                        throwable -> {
                            setOnboardingBusy(false);
                            onboardingStatusText.setText(
                                    getString(R.string.onboarding_failed, message(throwable)));
                        }));
    }

    private void setOnboardingBusy(boolean busy) {
        btnStartOnboarding.setEnabled(!busy);
        onboardingProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
    }
}
