package com.emvenhance.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.emvenhance.BuildConfig;
import com.emvenhance.R;
import com.emvenhance.network.HostHeaders;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.vendor.pax.PaxEmvParamUpdateService;
import com.google.android.material.button.MaterialButton;
import com.pax.configservice.impl.EmvParamUpdateResult;
import com.pax.configservice.impl.EmvParamUpdater;
import com.pax.poslib.model.ModelInfo;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * One category at a time: tap a category button to replace the fragment container with a
 * fresh {@link EmvParamListFragment} for it. Switching categories drops the previous fragment,
 * so only the category currently on screen is ever loaded.
 *
 * <p>Also the debug/setup home for downloading a fresh EMV parameter package —
 * {@link PaxEmvParamUpdateService#downloadAndApply} downloads and applies a fresh
 * {@code emv_param.emv}/{@code clss_param.clss} package over whatever {@code ConfigInit}'s
 * bundled-JSON first-boot seed (or an earlier sync) left in place; the status line below the
 * button reflects exactly what {@link EmvParamUpdateResult} reports — which sections applied and
 * their row counts, or the first error hit. A failed or never attempted sync leaves the
 * bundled/previous data as-is (see {@code EmvParamUpdater}'s javadoc), so the category browser
 * below always reflects the real current state.
 *
 * <p>Uses {@link HostHeaders#buildAuthenticated} for its request headers — the real, confirmed
 * post-onboarding contract (a captured {@code tmsFileDownload} call used it, not the pre-token
 * {@link HostHeaders#build}) — so onboarding must complete first; see {@link #downloadEmvParams},
 * which refreshes the saved token first via {@code OnboardingClient#ensureValidAccessToken} rather
 * than handing the host whatever token onboarding first minted, however old.
 */
public class EmvParamActivity extends AppCompatActivity {

    private final CompositeDisposable disposables = new CompositeDisposable();

    private MaterialButton btnDownloadEmvParams;
    private ProgressBar paramSyncProgress;
    private TextView paramSyncStatusText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emv_param);
        setTitle(R.string.action_show_emv_param);

        btnDownloadEmvParams = findViewById(R.id.btnDownloadEmvParams);
        paramSyncProgress = findViewById(R.id.paramSyncProgress);
        paramSyncStatusText = findViewById(R.id.paramSyncStatusText);
        btnDownloadEmvParams.setOnClickListener(v -> downloadEmvParams());
        // Debug builds keep the last downloaded files (EmvParamUpdater.saveForDiagnosis).
        View btnShowDownloadedXml = findViewById(R.id.btnShowDownloadedXml);
        btnShowDownloadedXml.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        btnShowDownloadedXml.setOnClickListener(v -> chooseDownloadedXml());

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
        OnboardingState state = new OnboardingState(this);
        if (state.getAccessToken() == null) {
            paramSyncStatusText.setText(R.string.emv_param_sync_credentials_required);
            return;
        }
        String sn = ModelInfo.getInstance().getSN();
        // Fixed vendor literal, not ModelInfo#getTerminalModel() — a real captured old-app log
        // hit foundation/tmsFileDownload/pax (200, real zip back) regardless of the terminal's own
        // model string; this flavor is always PAX, so the per-device model ("A920Pro" etc., what
        // getTerminalModel() actually returns) was the wrong value and 500'd on the host.
        String posType = "pax";

        setSyncBusy(true);
        paramSyncStatusText.setText(R.string.emv_param_sync_in_progress);

        // Refresh first if the saved token is near/past expiry — buildAuthenticated used to read
        // OnboardingState#getAccessToken() straight off, which reused a token forever and could
        // hand the host one already expired.
        Single<EmvParamUpdateResult> download = new OnboardingClient()
                .ensureValidAccessToken(HostHeaders.build(sn), state)
                .flatMap(accessToken -> new PaxEmvParamUpdateService()
                        .downloadAndApply(HostHeaders.buildAuthenticated(sn, accessToken), posType));

        disposables.add(download
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                            setSyncBusy(false);
                            paramSyncStatusText.setText(result.summarize());
                            if (result.changedDatabase()) {
                                // The database changed (rows applied or removed, even if a later
                                // section failed): the next transaction loads it again.
                                state.requireTerminalInitialization();
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

    private void setSyncBusy(boolean busy) {
        btnDownloadEmvParams.setEnabled(!busy);
        paramSyncProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
    }

    private void showCategory(EmvParamListFragment.Category category) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, EmvParamListFragment.newInstance(category))
                .commit();
    }

    /** Which of the last download's two files to show. */
    private void chooseDownloadedXml() {
        String[] files = {EmvParamUpdater.CONTACT_FILE, EmvParamUpdater.CONTACTLESS_FILE};
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_show_downloaded_xml)
                .setItems(files, (dialog, which) -> showDownloadedXml(files[which]))
                .show();
    }

    /** The file as the host sent it, selectable, with where it is on the device. */
    private void showDownloadedXml(String name) {
        File dir = EmvParamUpdater.diagnosisDir();
        File file = dir == null ? null : new File(dir, name);
        String text;
        if (file == null || !file.isFile()) {
            text = getString(R.string.downloaded_xml_missing, name);
        } else {
            try {
                text = file.getAbsolutePath() + "\n\n" + readText(file);
            } catch (IOException e) {
                text = file.getAbsolutePath() + "\n\n" + message(e);
            }
        }
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextIsSelectable(true);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(11);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static String readText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
