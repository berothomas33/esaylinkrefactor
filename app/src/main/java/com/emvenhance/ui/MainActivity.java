package com.emvenhance.ui;

import android.app.AlertDialog;
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
import com.pax.configservice.impl.ConfigInit;
import com.pax.configservice.impl.EmvParamService;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.common.CapkParam;
import com.pax.emvbase.param.contact.EmvAid;
import java.util.List;

/**
 * Observes {@link MainViewModel} only. Vendor card search is transparent via PosTerminal.
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

        binding.btnAcceptCard.setOnClickListener(
                v -> viewModel.acceptCard(PROC_CODE, AMOUNT_MINOR));
        binding.btnContact.setOnClickListener(
                v -> viewModel.startContact(PROC_CODE, AMOUNT_MINOR));
        binding.btnContactless.setOnClickListener(
                v -> viewModel.startContactless(PROC_CODE, AMOUNT_MINOR));
        binding.btnCancel.setOnClickListener(v -> viewModel.cancel());
        binding.btnInsertEmvParam.setOnClickListener(v -> insertEmvParam());
        binding.btnShowEmvParam.setOnClickListener(v -> showEmvParam());

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

    /** Re-parses the JSON assets and re-inserts AID/CAPK/scheme params into the local DB. */
    private void insertEmvParam() {
        new ConfigInit().init();
        toast("Inserting EMV param data…");
    }

    /**
     * Reads whatever is currently cached/stored and shows it — does not insert anything. Works
     * on any vendor: {@code BaseDaoHelper.getDaoSession()} opens the local DB itself on first
     * use, it no longer depends on {@code PaxTerminal} having run first.
     */
    private void showEmvParam() {
        new Thread(() -> {
            String summary;
            try {
                summary = summarize(new EmvParamService().getCachedEmvParam());
            } catch (Exception e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
                summary = "Failed to read EMV param: "
                        + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            String finalSummary = summary;
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.action_show_emv_param))
                    .setMessage(finalSummary)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
        }, "ShowEmvParam").start();
    }

    private static String summarize(EmvProcessParam param) {
        List<EmvAid> aids = param.getEmvAidList();
        CapkParam capk = param.getCapkParam();

        StringBuilder sb = new StringBuilder();
        sb.append("AID: ").append(aids != null ? aids.size() : 0).append('\n');
        if (aids != null) {
            for (EmvAid aid : aids) {
                sb.append("  • ").append(aid.getLocalAIDName()).append('\n');
            }
        }
        sb.append("CAPK: ")
                .append(capk != null && capk.getCapkList() != null ? capk.getCapkList().size() : 0)
                .append(" keys, ")
                .append(capk != null && capk.getCapkRevokeList() != null
                        ? capk.getCapkRevokeList().size() : 0)
                .append(" revoked\n");
        sb.append("Term config: ").append(loaded(param.getTermConfig())).append('\n');
        sb.append("PayWave: ").append(loaded(param.getPayWaveParam())).append('\n');
        sb.append("PayPass: ").append(loaded(param.getPayPassParam())).append('\n');
        sb.append("Amex: ").append(loaded(param.getAmexParam())).append('\n');
        sb.append("Dpas: ").append(loaded(param.getDpasParam())).append('\n');
        sb.append("EFT: ").append(loaded(param.getEftParam())).append('\n');
        sb.append("Jcb: ").append(loaded(param.getJcbParam())).append('\n');
        sb.append("Mir: ").append(loaded(param.getMirParam())).append('\n');
        sb.append("Pboc: ").append(loaded(param.getPbocParam())).append('\n');
        sb.append("Pure: ").append(loaded(param.getPureParam())).append('\n');
        sb.append("RuPay: ").append(loaded(param.getRuPayParam()));
        return sb.toString();
    }

    private static String loaded(Object param) {
        return param != null ? "loaded" : "missing";
    }
}
