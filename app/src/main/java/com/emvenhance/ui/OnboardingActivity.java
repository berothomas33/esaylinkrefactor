package com.emvenhance.ui;

import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.emvenhance.EmvEnhanceApp;
import com.emvenhance.R;
import com.emvenhance.network.HostHeaders;
import com.emvenhance.network.HostSettings;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.network.onboarding.model.CreateTokenResult;
import com.emvenhance.settings.PinBypassSettings;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class OnboardingActivity extends AppCompatActivity {

    private final CompositeDisposable disposables = new CompositeDisposable();

    private MaterialButton btnStartOnboarding;
    private ProgressBar onboardingProgress;
    private TextView onboardingStatusText;
    private HostSettings hostSettings;
    private EditText mTokenInput;
    private TextView mTokenSourceText;
    private TextView headersText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        setTitle(R.string.label_onboarding);

        btnStartOnboarding = findViewById(R.id.btnStartOnboarding);
        onboardingProgress = findViewById(R.id.onboardingProgress);
        onboardingStatusText = findViewById(R.id.onboardingStatusText);
        btnStartOnboarding.setOnClickListener(v -> startOnboarding());

        PinBypassSettings pinBypass = new PinBypassSettings(this);
        SwitchMaterial onlinePinBypass = findViewById(R.id.switchOnlinePinBypass);
        SwitchMaterial offlinePinBypass = findViewById(R.id.switchOfflinePinBypass);
        onlinePinBypass.setChecked(pinBypass.isOnlinePinBypassEnabled());
        offlinePinBypass.setChecked(pinBypass.isOfflinePinBypassEnabled());
        onlinePinBypass.setOnCheckedChangeListener(
                (button, checked) -> pinBypass.setOnlinePinBypassEnabled(checked));
        offlinePinBypass.setOnCheckedChangeListener(
                (button, checked) -> pinBypass.setOfflinePinBypassEnabled(checked));

        hostSettings = new HostSettings(this);
        mTokenInput = findViewById(R.id.mTokenInput);
        mTokenSourceText = findViewById(R.id.mTokenSourceText);
        headersText = findViewById(R.id.headersText);
        mTokenInput.setText(hostSettings.getMToken());
        findViewById(R.id.btnSaveMToken).setOnClickListener(v -> {
            hostSettings.saveMToken(mTokenInput.getText().toString());
            mTokenInput.setText(hostSettings.getMToken());
            toast(hostSettings.isMTokenOverridden() ? R.string.mtoken_saved : R.string.mtoken_reset);
            refreshHeaderInfo();
        });
        findViewById(R.id.btnResetMToken).setOnClickListener(v -> {
            hostSettings.resetMToken();
            mTokenInput.setText(hostSettings.getMToken());
            toast(R.string.mtoken_reset);
            refreshHeaderInfo();
        });
        refreshHeaderInfo();
    }

    private void refreshHeaderInfo() {
        String mToken = hostSettings.getMToken();
        String source = getString(hostSettings.isMTokenOverridden()
                ? R.string.mtoken_source_saved : R.string.mtoken_source_fixed);
        String expiry = jwtExpiry(mToken);
        mTokenSourceText.setText(expiry != null ? source + "\n" + expiry : source);

        String accessToken = new OnboardingState(this).getAccessToken();
        Map<String, String> headers = HostHeaders.describeAll(
                serialNumber(), accessToken, mToken);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(header.getKey()).append(":\n").append(header.getValue());
        }
        headersText.setText(sb);
    }

    @Nullable
    private static String jwtExpiry(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payload = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            long exp = new JSONObject(new String(payload, StandardCharsets.UTF_8)).optLong("exp", 0);
            if (exp <= 0) {
                return null;
            }
            Date expiry = new Date(exp * 1000L);
            String when = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(expiry);
            return expiry.before(new Date()) ? "EXPIRED on " + when : "Expires " + when;
        } catch (Exception e) {
            return null;
        }
    }

    private void toast(int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private void startOnboarding() {
        setOnboardingBusy(true);
        onboardingStatusText.setText(R.string.onboarding_in_progress);

        Map<String, String> headers = HostHeaders.build(serialNumber());

        OnboardingState state = new OnboardingState(this);
        Single<CreateTokenResult> cycle = new OnboardingClient().runOfflineCycle(headers, state);

        disposables.add(cycle
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                            setOnboardingBusy(false);
                            onboardingStatusText.setText(R.string.onboarding_succeeded);
                            refreshHeaderInfo();
                        },
                        throwable -> {
                            setOnboardingBusy(false);
                            onboardingStatusText.setText(
                                    getString(R.string.onboarding_failed, message(throwable)));
                        }));
    }

    private String serialNumber() {
        return ((EmvEnhanceApp) getApplication()).getTerminal().serialNumber();
    }

    private void setOnboardingBusy(boolean busy) {
        btnStartOnboarding.setEnabled(!busy);
        onboardingProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
    }
}
