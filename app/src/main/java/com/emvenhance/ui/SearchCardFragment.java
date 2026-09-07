package com.emvenhance.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
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

        view.findViewById(R.id.btnCancelSearch).setOnClickListener(v -> {
            viewModel.cancel();
            requireActivity().finish();
        });

        bindKvRow(view, R.id.rowPan, getString(R.string.label_pan_result));
        bindKvRow(view, R.id.rowTvr, getString(R.string.label_tvr));
        bindKvRow(view, R.id.rowTacDenial, getString(R.string.label_tac_denial));
        bindKvRow(view, R.id.rowIacDenial, getString(R.string.label_iac_denial));
        bindKvRow(view, R.id.rowIccData, getString(R.string.label_icc_data));

        MaterialCheckBox chkApduLog = view.findViewById(R.id.chkApduLog);
        chkApduLog.setChecked(viewModel.isApduLoggingEnabled());
        chkApduLog.setOnCheckedChangeListener((btn, checked) ->
                viewModel.setApduLoggingEnabled(checked));

        View resultGroup = view.findViewById(R.id.resultGroup);
        view.findViewById(R.id.btnPrintResult).setOnClickListener(v -> {
            viewModel.printReceipt(buildResultLines(view));
            Toast.makeText(requireContext(), R.string.result_printed, Toast.LENGTH_SHORT).show();
        });

        // Once a card is actually detected, the "insert / swipe / tap / manual" choices no
        // longer describe what's happening — hide them instead of leaving a stale, misleading
        // prompt on screen while EMV runs (tracked by emvStepBanner above instead). The result
        // panel is the mirror image: hidden until the transaction actually finishes, chip/
        // contactless only — mag/manual have no TVR/TAC/IAC/Field 55 to show (§ PaxEmvBehavior
        // captureTransactionSummary), so their rows just read "—".
        View methodSelectionGroup = view.findViewById(R.id.methodSelectionGroup);
        viewModel.getTransactionStep().observe(getViewLifecycleOwner(), event -> {
            TransactionStep step = event.getStep();
            boolean stillChoosing = step == TransactionStep.IDLE
                    || step == TransactionStep.TRANSACTION_STARTED
                    || step == TransactionStep.WAITING_FOR_CARD;
            methodSelectionGroup.setVisibility(stillChoosing ? View.VISIBLE : View.GONE);

            boolean finished = step == TransactionStep.APPROVED || step == TransactionStep.DECLINED;
            resultGroup.setVisibility(finished ? View.VISIBLE : View.GONE);
            if (finished) {
                setKvValue(view, R.id.rowPan, event.getString(TransactionStepEvent.KEY_PAN));
                setKvValue(view, R.id.rowTvr, event.getString(TransactionStepEvent.KEY_TVR));
                setKvValue(view, R.id.rowTacDenial, event.getString(TransactionStepEvent.KEY_TAC_DENIAL));
                setKvValue(view, R.id.rowIacDenial, event.getString(TransactionStepEvent.KEY_IAC_DENIAL));
                setKvValue(view, R.id.rowIccData, event.getString(TransactionStepEvent.KEY_ICC_DATA));
            }
        });

        // Fire the search the moment this screen is up, so the reader is live as soon as the
        // cardholder sees "present card" — matches AmountFragment's old behavior, just moved
        // here now that there's a dedicated screen for it.
        viewModel.acceptCard(type, amountMinor);
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
