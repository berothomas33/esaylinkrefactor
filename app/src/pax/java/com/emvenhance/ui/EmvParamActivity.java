package com.emvenhance.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.emvenhance.R;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.network.onboarding.model.OnboardingStatusResponse;
import com.emvenhance.vendor.pax.PaxEmvParamUpdateService;
import com.google.android.material.button.MaterialButton;
import com.pax.configservice.impl.EmvParamUpdateResult;
import com.pax.poslib.model.ModelInfo;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * One category at a time: tap a category button to replace the fragment container with a
 * fresh {@link EmvParamListFragment} for it. Switching categories drops the previous fragment,
 * so only the category currently on screen is ever loaded.
 *
 * <p>Also the debug/setup home for the two host-facing flows built on top of this data:
 * <ul>
 *   <li><b>EMV parameter sync</b> — {@link PaxEmvParamUpdateService#downloadAndApply} downloads
 *       and applies a fresh {@code emv_param.emv}/{@code clss_param.clss} package over whatever
 *       {@code ConfigInit}'s bundled-JSON first-boot seed (or an earlier sync) left in place; the
 *       status line below the button reflects exactly what {@link EmvParamUpdateResult} reports
 *       — which sections applied and their row counts, or the first error hit. A failed or never
 *       attempted sync leaves the bundled/previous data as-is (see {@code EmvParamUpdater}'s
 *       javadoc), so the category browser below always reflects the real current state.
 *   <li><b>Onboarding</b> — {@link OnboardingClient#runOfflineCycle} runs the handshake → onboard
 *       → confirm cycle once and reports success/failure.
 * </ul>
 * Both need caller-supplied headers the app has no session/auth layer to build yet — the Account
 * ID / Token fields above them are used verbatim as {@code Account-Id} / a bearer
 * {@code Authorization} header (see {@link #buildHeaders}), a placeholder convention until the
 * real header contract is known.
 *
 * <p>Also the only place these credentials get entered at all: {@link #requireHeaders} persists
 * them via {@link OnboardingState#saveAccountId}/{@link OnboardingState#saveToken} on every
 * successful use, and {@link #onCreate} pre-fills the fields from there — so
 * {@code SaleCommunicationBehavior}, run later from {@code PaxTerminal} with no UI in the loop,
 * has something to read.
 */
public class EmvParamActivity extends AppCompatActivity {

    private final CompositeDisposable disposables = new CompositeDisposable();

    private EditText accountIdInput;
    private EditText tokenInput;

    private MaterialButton btnDownloadEmvParams;
    private ProgressBar paramSyncProgress;
    private TextView paramSyncStatusText;

    private MaterialButton btnStartOnboarding;
    private ProgressBar onboardingProgress;
    private TextView onboardingStatusText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emv_param);
        setTitle(R.string.action_show_emv_param);

        accountIdInput = findViewById(R.id.accountIdInput);
        tokenInput = findViewById(R.id.tokenInput);
        OnboardingState savedState = new OnboardingState(this);
        if (savedState.getAccountId() != null) {
            accountIdInput.setText(savedState.getAccountId());
        }
        if (savedState.getToken() != null) {
            tokenInput.setText(savedState.getToken());
        }

        btnDownloadEmvParams = findViewById(R.id.btnDownloadEmvParams);
        paramSyncProgress = findViewById(R.id.paramSyncProgress);
        paramSyncStatusText = findViewById(R.id.paramSyncStatusText);
        btnDownloadEmvParams.setOnClickListener(v -> downloadEmvParams());

        btnStartOnboarding = findViewById(R.id.btnStartOnboarding);
        onboardingProgress = findViewById(R.id.onboardingProgress);
        onboardingStatusText = findViewById(R.id.onboardingStatusText);
        btnStartOnboarding.setOnClickListener(v -> startOnboarding());

        LinearLayout categoryRow = findViewById(R.id.categoryRow);
        for (EmvParamListFragment.Category category : EmvParamListFragment.Category.values()) {
            MaterialButton button = new MaterialButton(this);
            button.setText(category.label);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            button.setLayoutParams(params);
            button.setOnClickListener(v -> showCategory(category));
            categoryRow.addView(button);
        }

        if (savedInstanceState == null) {
            showCategory(EmvParamListFragment.Category.AID);
        }
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private void downloadEmvParams() {
        Map<String, String> headers = requireHeaders(paramSyncStatusText,
                R.string.emv_param_sync_credentials_required);
        if (headers == null) {
            return;
        }

        setSyncBusy(true);
        paramSyncStatusText.setText(R.string.emv_param_sync_in_progress);

        String posType = ModelInfo.getInstance().getTerminalModel();
        Single<EmvParamUpdateResult> download = new PaxEmvParamUpdateService()
                .downloadAndApply(headers, posType);

        disposables.add(download
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                            setSyncBusy(false);
                            paramSyncStatusText.setText(result.summarize());
                            if (result.isSuccess() && !result.getAppliedSections().isEmpty()) {
                                // Refresh whichever category is on screen so an applied download
                                // shows up immediately instead of waiting for the next tab switch.
                                showCategory(EmvParamListFragment.Category.AID);
                            }
                        },
                        throwable -> {
                            setSyncBusy(false);
                            paramSyncStatusText.setText(
                                    getString(R.string.emv_param_sync_failed, message(throwable)));
                        }));
    }

    private void startOnboarding() {
        Map<String, String> headers = requireHeaders(onboardingStatusText,
                R.string.onboarding_credentials_required);
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
    private Map<String, String> requireHeaders(TextView statusText, int missingCredentialsRes) {
        String accountId = accountIdInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        if (TextUtils.isEmpty(accountId) || TextUtils.isEmpty(token)) {
            statusText.setText(missingCredentialsRes);
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
     * is known (see {@code RetrofitCommunicationBehavior}/{@code OnboardingClient}, neither of
     * which build headers themselves either; every caller supplies its own).
     */
    private static Map<String, String> buildHeaders(String accountId, String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Account-Id", accountId);
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    private void setSyncBusy(boolean busy) {
        btnDownloadEmvParams.setEnabled(!busy);
        paramSyncProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void setOnboardingBusy(boolean busy) {
        btnStartOnboarding.setEnabled(!busy);
        onboardingProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
    }

    private void showCategory(EmvParamListFragment.Category category) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, EmvParamListFragment.newInstance(category))
                .commit();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
