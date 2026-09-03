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
import com.emvenhance.core.event.EmvStepEvent;
import java.text.NumberFormat;
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

    private static final String ARG_PROC_CODE = "procCode";
    private static final String ARG_AMOUNT_MINOR = "amountMinor";

    public static SearchCardFragment newInstance(String procCode, long amountMinor) {
        Bundle args = new Bundle();
        args.putString(ARG_PROC_CODE, procCode);
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
        String procCode = args.getString(ARG_PROC_CODE, "000000");
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

        // Fire the search the moment this screen is up, so the reader is live as soon as the
        // cardholder sees "present card" — matches AmountFragment's old behavior, just moved
        // here now that there's a dedicated screen for it.
        viewModel.acceptCard(procCode, amountMinor);
    }

    private static void bindMethodRow(View parent, int rowId, String badge, String title,
            String subtitle) {
        View row = parent.findViewById(rowId);
        ((TextView) row.findViewById(R.id.methodBadge)).setText(badge);
        ((TextView) row.findViewById(R.id.methodTitle)).setText(title);
        ((TextView) row.findViewById(R.id.methodSubtitle)).setText(subtitle);
    }

    private static String formatAmount(long amountMinor) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amountMinor / 100.0);
    }
}
