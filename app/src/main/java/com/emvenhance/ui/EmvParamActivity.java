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
import com.emvenhance.EmvEnhanceApp;
import com.emvenhance.R;
import com.emvenhance.core.param.EmvParams;
import com.emvenhance.network.onboarding.OnboardingState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class EmvParamActivity extends AppCompatActivity {

    private static final String STATE_CATEGORY = "category";

    private final CompositeDisposable disposables = new CompositeDisposable();

    private EmvParams params;
    private String category;
    private boolean fromCache = true;
    private MaterialButton btnDownloadEmvParams;
    private ProgressBar paramSyncProgress;
    private TextView paramSyncStatusText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emv_param);
        setTitle(R.string.action_show_emv_param);
        params = ((EmvEnhanceApp) getApplication()).getTerminal().emvParams();
        if (params == null) {
            finish();
            return;
        }

        btnDownloadEmvParams = findViewById(R.id.btnDownloadEmvParams);
        paramSyncProgress = findViewById(R.id.paramSyncProgress);
        paramSyncStatusText = findViewById(R.id.paramSyncStatusText);
        btnDownloadEmvParams.setOnClickListener(v -> downloadEmvParams());
        View btnShowDownloadedXml = findViewById(R.id.btnShowDownloadedXml);
        btnShowDownloadedXml.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        btnShowDownloadedXml.setOnClickListener(v -> chooseDownloadedXml());

        LinearLayout categoryRow = findViewById(R.id.categoryRow);
        for (String label : params.categories()) {
            MaterialButton button = new MaterialButton(this);
            button.setText(label);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layout.setMarginEnd(dp(8));
            button.setLayoutParams(layout);
            button.setOnClickListener(v -> showCategory(label));
            categoryRow.addView(button);
        }

        MaterialButtonToggleGroup sourceToggle = findViewById(R.id.sourceToggle);
        category = savedInstanceState != null
                ? savedInstanceState.getString(STATE_CATEGORY) : params.categories().get(0);
        fromCache = sourceToggle.getCheckedButtonId() == R.id.btnSourceCache;
        sourceToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                fromCache = checkedId == R.id.btnSourceCache;
                showCategory(category);
            }
        });

        if (savedInstanceState == null) {
            showCategory(category);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CATEGORY, category);
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private void downloadEmvParams() {
        if (new OnboardingState(this).getAccessToken() == null) {
            paramSyncStatusText.setText(R.string.emv_param_sync_credentials_required);
            return;
        }
        setSyncBusy(true);
        paramSyncStatusText.setText(R.string.emv_param_sync_in_progress);

        disposables.add(params.download()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        summary -> {
                            setSyncBusy(false);
                            paramSyncStatusText.setText(summary);
                            showCategory(category);
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

    private void showCategory(String category) {
        this.category = category;
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer,
                        EmvParamListFragment.newInstance(category, fromCache))
                .commit();
    }

    private void chooseDownloadedXml() {
        String[] files = params.downloadedFiles().toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_show_downloaded_xml)
                .setItems(files, (dialog, which) -> showDownloadedXml(files[which]))
                .show();
    }

    private void showDownloadedXml(String name) {
        File file = params.downloadedFile(name);
        String text;
        if (file == null) {
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
