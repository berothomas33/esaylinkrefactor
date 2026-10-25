package com.emvenhance.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.emvenhance.BuildConfig;
import com.emvenhance.EmvEnhanceApp;
import com.emvenhance.R;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.google.android.material.checkbox.MaterialCheckBox;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchCardFragment extends Fragment {

    private static final String ARG_TRANSACTION_TYPE = "transactionType";
    private static final String ARG_AMOUNT_MINOR = "amountMinor";
    private static final String ARG_REFERENCE_NUMBER = "referenceNumber";

    private static final int PRINT_WIDTH = 32;

    private Disposable steps = Disposable.disposed();

    public static SearchCardFragment newInstance(TransactionType type, long amountMinor,
            @Nullable String referenceNumber) {
        Bundle args = new Bundle();
        args.putString(ARG_TRANSACTION_TYPE, type.name());
        args.putLong(ARG_AMOUNT_MINOR, amountMinor);
        args.putString(ARG_REFERENCE_NUMBER, referenceNumber);
        SearchCardFragment fragment = new SearchCardFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_card, container, false);
    }

    @Override
    public void onDestroyView() {
        steps.dispose();
        super.onDestroyView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        TransactionType type = TransactionType.valueOf(
                args.getString(ARG_TRANSACTION_TYPE, TransactionType.SALE.name()));
        long amountMinor = args.getLong(ARG_AMOUNT_MINOR, 0);
        String referenceNumber = args.getString(ARG_REFERENCE_NUMBER);

        MainViewModel viewModel = new ViewModelProvider(requireActivity(),
                new MainViewModelFactory(((EmvEnhanceApp) requireActivity().getApplication()).getTerminal()))
                .get(MainViewModel.class);

        ((TextView) view.findViewById(R.id.amountText)).setText(formatAmount(amountMinor));

        TextView emvStepBanner = view.findViewById(R.id.emvStepBanner);
        viewModel.getEmvStep().observe(getViewLifecycleOwner(), event -> {
            EmvStepEvent emvEvent = event.consume();
            if (emvEvent != null) {
                emvStepBanner.setText(emvEvent.toString());
            }
        });

        bindMethodRow(view, R.id.rowMagstripe, "MAG",
                getString(R.string.method_magstripe_title),
                getString(R.string.method_magstripe_subtitle));
        bindMethodRow(view, R.id.rowChip, "CHIP",
                getString(R.string.method_chip_title),
                getString(R.string.method_chip_subtitle));
        bindMethodRow(view, R.id.rowContactless, "NFC",
                getString(R.string.method_contactless_title),
                getString(R.string.method_contactless_subtitle));

        view.findViewById(R.id.btnManualEntry).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.manual_entry_not_implemented,
                        Toast.LENGTH_SHORT).show());

        View btnCancelSearch = view.findViewById(R.id.btnCancelSearch);
        btnCancelSearch.setOnClickListener(v -> cancelAndClose(viewModel));
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        cancelAndClose(viewModel);
                    }
                });

        View outcomeGroup = view.findViewById(R.id.outcomeGroup);
        view.findViewById(R.id.btnOutcomeDone).setOnClickListener(v -> requireActivity().finish());
        view.findViewById(R.id.btnOutcomeRetry).setOnClickListener(v -> {
            outcomeGroup.setVisibility(View.GONE);
            viewModel.acceptCard(type, amountMinor, referenceNumber);
        });

        bindKvRow(view, R.id.rowPan, getString(R.string.label_pan_result));
        bindKvRow(view, R.id.rowTvr, getString(R.string.label_tvr));
        bindKvRow(view, R.id.rowTacDenial, getString(R.string.label_tac_denial));
        bindKvRow(view, R.id.rowIacDenial, getString(R.string.label_iac_denial));
        bindKvRow(view, R.id.rowIccData, getString(R.string.label_icc_data));

        bindApduTrace(view, viewModel);

        View resultGroup = view.findViewById(R.id.resultGroup);
        view.findViewById(R.id.btnPrintResult).setOnClickListener(v -> {
            List<String> lines = buildResultLines(view);
            List<String> trace = viewModel.getApduTracePrintLines(PRINT_WIDTH);
            if (!trace.isEmpty()) {
                lines.add("");
                lines.addAll(trace);
            }
            viewModel.printReceipt(lines);
            Toast.makeText(requireContext(), R.string.result_printed, Toast.LENGTH_SHORT).show();
        });

        View methodSelectionGroup = view.findViewById(R.id.methodSelectionGroup);
        TextView retryPromptText = view.findViewById(R.id.retryPromptText);
        java.util.function.Consumer<TransactionStepEvent> render = event -> {
            TransactionStep step = event.getStep();
            boolean stillChoosing = step == TransactionStep.IDLE
                    || step == TransactionStep.TRANSACTION_STARTED
                    || step == TransactionStep.WAITING_FOR_CARD;
            methodSelectionGroup.setVisibility(stillChoosing ? View.VISIBLE : View.GONE);

            if (step == TransactionStep.WAITING_FOR_CARD) {
                Object prompt = event.get(TransactionStepEvent.KEY_RETRY_PROMPT);
                retryPromptText.setText(prompt != null ? prompt.toString() : "");
                retryPromptText.setVisibility(prompt != null ? View.VISIBLE : View.GONE);
                if (prompt != null && view instanceof ScrollView) {
                    view.post(() -> ((ScrollView) view).smoothScrollTo(0, 0));
                }
            }

            switch (step) {
                case APPROVED:
                    showOutcome(view, R.string.outcome_approved, true,
                            event.get(TransactionStepEvent.KEY_RESULT));
                    btnCancelSearch.setVisibility(View.GONE);
                    break;
                case DECLINED:
                    showOutcome(view, R.string.outcome_declined, false,
                            event.get(TransactionStepEvent.KEY_ERROR));
                    btnCancelSearch.setVisibility(View.GONE);
                    break;
                case ERROR:
                    showOutcome(view, R.string.outcome_failed, false, event.getMessage() != null
                            ? event.getMessage() : event.get(TransactionStepEvent.KEY_ERROR));
                    btnCancelSearch.setVisibility(View.GONE);
                    break;
                case CANCELLED:
                    showOutcome(view, R.string.outcome_cancelled, false, null);
                    btnCancelSearch.setVisibility(View.GONE);
                    break;
                case TRANSACTION_STARTED:
                    outcomeGroup.setVisibility(View.GONE);
                    btnCancelSearch.setVisibility(View.VISIBLE);
                    break;
                default:
                    break;
            }

            if (step == TransactionStep.APPROVED || step == TransactionStep.DECLINED) {
                resultGroup.setVisibility(View.VISIBLE);
                setKvValue(view, R.id.rowPan, event.getString(TransactionStepEvent.KEY_PAN));
                setKvValue(view, R.id.rowTvr, event.getString(TransactionStepEvent.KEY_TVR));
                setKvValue(view, R.id.rowTacDenial, event.getString(TransactionStepEvent.KEY_TAC_DENIAL));
                setKvValue(view, R.id.rowIacDenial, event.getString(TransactionStepEvent.KEY_IAC_DENIAL));
                setKvValue(view, R.id.rowIccData, event.getString(TransactionStepEvent.KEY_ICC_DATA));
            } else if (stillChoosing) {
                resultGroup.setVisibility(View.GONE);
            }
        };

        viewModel.getTransaction().observe(getViewLifecycleOwner(), transaction -> {
            steps.dispose();
            if (transaction == null) {
                render.accept(TransactionStepEvent.idle());
                return;
            }
            steps = transaction.steps()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(render::accept);
        });

        viewModel.getTerminalBusy().observe(getViewLifecycleOwner(), event -> {
            if (event.consume() != null) {
                showBusy(view);
            }
        });

        if (savedInstanceState == null) {
            viewModel.acceptCard(type, amountMinor, referenceNumber);
        }
    }

    private void bindApduTrace(View view, MainViewModel viewModel) {
        View group = view.findViewById(R.id.apduTraceGroup);
        if (!BuildConfig.DEBUG) {
            group.setVisibility(View.GONE);
            return;
        }

        MaterialCheckBox chkApduLog = view.findViewById(R.id.chkApduLog);
        chkApduLog.setChecked(viewModel.isApduLoggingEnabled());
        chkApduLog.setOnCheckedChangeListener((btn, checked) ->
                viewModel.setApduLoggingEnabled(checked));

        EditText traceText = view.findViewById(R.id.apduTraceText);
        traceText.setShowSoftInputOnFocus(false);
        viewModel.getApduTrace().observe(getViewLifecycleOwner(), traceText::setText);

        view.findViewById(R.id.btnCopyApdu).setOnClickListener(v -> {
            String trace = traceText.getText().toString();
            if (trace.isEmpty()) {
                Toast.makeText(requireContext(), R.string.apdu_trace_nothing, Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard =
                    (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("APDU trace", trace));
            Toast.makeText(requireContext(), R.string.apdu_trace_copied, Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btnPrintApdu).setOnClickListener(v -> {
            List<String> trace = viewModel.getApduTracePrintLines(PRINT_WIDTH);
            if (trace.isEmpty()) {
                Toast.makeText(requireContext(), R.string.apdu_trace_nothing, Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.printReceipt(trace);
            Toast.makeText(requireContext(), R.string.result_printed, Toast.LENGTH_SHORT).show();
        });
    }

    private void cancelAndClose(MainViewModel viewModel) {
        viewModel.cancel();
        requireActivity().finish();
    }

    private static void showOutcome(View view, @StringRes int title, boolean approved,
            @Nullable Object reason) {
        View group = view.findViewById(R.id.outcomeGroup);
        group.setBackgroundColor(approved ? 0xFF2E7D32 : 0xFFC62828);
        ((TextView) view.findViewById(R.id.outcomeTitle)).setText(title);
        TextView reasonText = view.findViewById(R.id.outcomeReason);
        reasonText.setText(reason != null ? reason.toString() : "");
        reasonText.setVisibility(reason != null ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btnOutcomeRetry).setVisibility(approved ? View.GONE : View.VISIBLE);
        group.setVisibility(View.VISIBLE);

        TextView banner = view.findViewById(R.id.emvStepBanner);
        banner.setText(title);
    }

    private static void showBusy(View view) {
        showOutcome(view, R.string.outcome_terminal_busy, false,
                view.getContext().getString(R.string.terminal_busy_reason));
        view.findViewById(R.id.methodSelectionGroup).setVisibility(View.GONE);
        view.findViewById(R.id.btnCancelSearch).setVisibility(View.GONE);
    }

    private static void bindMethodRow(View parent, int rowId, String badge, String title,
            String subtitle) {
        View row = parent.findViewById(rowId);
        ((TextView) row.findViewById(R.id.methodBadge)).setText(badge);
        ((TextView) row.findViewById(R.id.methodTitle)).setText(title);
        ((TextView) row.findViewById(R.id.methodSubtitle)).setText(subtitle);
    }

    private static void bindKvRow(View parent, int rowId, String label) {
        ((TextView) parent.findViewById(rowId).findViewById(R.id.kvLabel)).setText(label);
    }

    private static void setKvValue(View parent, int rowId, String value) {
        ((TextView) parent.findViewById(rowId).findViewById(R.id.kvValue)).setText(value);
    }

    private static List<String> buildResultLines(View view) {
        List<String> lines = new ArrayList<>();
        lines.add(kvLine(view, R.id.rowPan));
        lines.add(kvLine(view, R.id.rowTvr));
        lines.add(kvLine(view, R.id.rowTacDenial));
        lines.add(kvLine(view, R.id.rowIacDenial));
        lines.add(kvLine(view, R.id.rowIccData));
        return lines;
    }

    private static String kvLine(View parent, int rowId) {
        View row = parent.findViewById(rowId);
        String label = ((TextView) row.findViewById(R.id.kvLabel)).getText().toString();
        String value = ((TextView) row.findViewById(R.id.kvValue)).getText().toString();
        return label + ": " + value;
    }

    private static String formatAmount(long amountMinor) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amountMinor / 100.0);
    }
}
