package com.emvenhance.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shown once the amount is confirmed: a live {@link com.emvenhance.core.event.EmvStep} banner
 * plus the accepted entry methods, while {@link MainViewModel#acceptCard} searches.
 *
 * <p>The three method rows are informational, not separately actionable — {@code acceptCard}
 * already searches mag/chip/contactless together (entry method {@code ANY}); tapping a physical
 * card of any of those kinds is what actually matters. Manual Entry is the one real exception,
 * since a reader can't detect "the operator wants to type a PAN" on its own — that flow doesn't
 * exist in this app yet, so the button is a stub for now.
 */
public class SearchCardFragment extends Fragment {

    private static final String ARG_TRANSACTION_TYPE = "transactionType";
    private static final String ARG_AMOUNT_MINOR = "amountMinor";

    /** Characters per printed line: 384-dot head with PaxPrinter's 12x24 font. */
    private static final int PRINT_WIDTH = 32;

    public static SearchCardFragment newInstance(TransactionType type, long amountMinor) {
        Bundle args = new Bundle();
        args.putString(ARG_TRANSACTION_TYPE, type.name());
        args.putLong(ARG_AMOUNT_MINOR, amountMinor);
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        TransactionType type = TransactionType.valueOf(
                args.getString(ARG_TRANSACTION_TYPE, TransactionType.SALE.name()));
        long amountMinor = args.getLong(ARG_AMOUNT_MINOR, 0);

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
        btnCancelSearch.setOnClickListener(v -> {
            viewModel.cancel();
            requireActivity().finish();
        });

        View outcomeGroup = view.findViewById(R.id.outcomeGroup);
        view.findViewById(R.id.btnOutcomeDone).setOnClickListener(v -> requireActivity().finish());
        view.findViewById(R.id.btnOutcomeRetry).setOnClickListener(v -> {
            outcomeGroup.setVisibility(View.GONE);
            viewModel.acceptCard(type, amountMinor);
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

        // Once a card is actually detected, the "insert / swipe / tap / manual" choices no
        // longer describe what's happening — hide them instead of leaving a stale, misleading
        // prompt on screen while EMV runs (tracked by emvStepBanner above instead). The result
        // panel is the mirror image: hidden until the transaction actually finishes, chip/
        // contactless only — mag/manual have no TVR/TAC/IAC/Field 55 to show (§ PaxEmvBehavior
        // captureTransactionSummary), so their rows just read "—".
        View methodSelectionGroup = view.findViewById(R.id.methodSelectionGroup);
        // The engine replays its last step to a new subscriber, so this screen can first receive
        // the previous transaction's ERROR/COMPLETED. Only react to an outcome once this screen's
        // own transaction has started.
        boolean[] started = {false};
        viewModel.getTransactionStep().observe(getViewLifecycleOwner(), event -> {
            TransactionStep step = event.getStep();
            boolean stillChoosing = step == TransactionStep.IDLE
                    || step == TransactionStep.TRANSACTION_STARTED
                    || step == TransactionStep.WAITING_FOR_CARD;
            if (step == TransactionStep.TRANSACTION_STARTED
                    || step == TransactionStep.WAITING_FOR_CARD) {
                started[0] = true;
            }
            if (!started[0]) {
                return;
            }
            methodSelectionGroup.setVisibility(stillChoosing ? View.VISIBLE : View.GONE);

            // Final outcome. A card-search timeout, reader failure, kernel/PIN error or host
            // failure all end in ERROR (never followed by COMPLETED); a decline ends in DECLINED
            // — the cardholder sees both as a declined transaction, with the reason under it.
            switch (step) {
                case APPROVED:
                    showOutcome(view, true, event.get(TransactionStepEvent.KEY_RESULT));
                    btnCancelSearch.setVisibility(View.GONE);
                    break;
                case DECLINED:
                    showOutcome(view, false, event.get(TransactionStepEvent.KEY_ERROR));
                    btnCancelSearch.setVisibility(View.GONE);
                    break;
                case ERROR:
                    showOutcome(view, false, event.getMessage() != null
                            ? event.getMessage() : event.get(TransactionStepEvent.KEY_ERROR));
                    btnCancelSearch.setVisibility(View.GONE);
                    break;
                case TRANSACTION_STARTED:
                    outcomeGroup.setVisibility(View.GONE);
                    btnCancelSearch.setVisibility(View.VISIBLE);
                    break;
                default:
                    break;
            }

            // APPROVED/DECLINED is immediately followed by a separate COMPLETED event (engine's
            // notifyCompleted(), fired right after) — both land on this same observer within the
            // same main-thread burst, before a frame ever draws. Hiding resultGroup again on
            // that COMPLETED event (or on ERROR, or on anything else) meant the panel was set
            // VISIBLE then GONE before Android ever painted it — never actually seen. Only ever
            // hide it when a *new* transaction starts; every other step leaves it as it is.
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
        });

        // Fire the search the moment this screen is up, so the reader is live as soon as the
        // cardholder sees "present card" — matches AmountFragment's old behavior, just moved
        // here now that there's a dedicated screen for it.
        viewModel.acceptCard(type, amountMinor);
    }

    /**
     * APDU trace card: debug builds only (the trace holds card data and is never recorded in a
     * release build). The EditText is filled live, with the soft keyboard suppressed, so it can be
     * scrolled, selected and copied — over Vysor too, or with the Copy button.
     */
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

    private static void showOutcome(View view, boolean approved, @Nullable Object reason) {
        View group = view.findViewById(R.id.outcomeGroup);
        group.setBackgroundColor(approved ? 0xFF2E7D32 : 0xFFC62828);
        ((TextView) view.findViewById(R.id.outcomeTitle)).setText(
                approved ? R.string.outcome_approved : R.string.outcome_declined);
        TextView reasonText = view.findViewById(R.id.outcomeReason);
        reasonText.setText(reason != null ? reason.toString() : "");
        reasonText.setVisibility(reason != null ? View.VISIBLE : View.GONE);
        // Retry only makes sense after a failure; an approved sale is finished.
        view.findViewById(R.id.btnOutcomeRetry).setVisibility(approved ? View.GONE : View.VISIBLE);
        group.setVisibility(View.VISIBLE);

        TextView banner = view.findViewById(R.id.emvStepBanner);
        banner.setText(approved ? R.string.outcome_approved : R.string.outcome_declined);
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

    /** Reads back what's currently on screen — the same values the result rows just set. */
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
