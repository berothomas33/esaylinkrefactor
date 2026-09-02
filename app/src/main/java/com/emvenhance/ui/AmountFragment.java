package com.emvenhance.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.emvenhance.EmvEnhanceApp;
import com.emvenhance.R;

/**
 * Collects the transaction amount and starts it. The only entry point left into a transaction —
 * {@link MainActivity}'s old "present card / chip only / contactless only" buttons always used a
 * hardcoded amount and are gone; this fragment is what replaces them, via {@link
 * MainViewModel#acceptCard} — the same "any entry method" path, same ViewModel bridge, {@link
 * MainActivity} used, so UI code never talks to {@code PosTerminal} directly.
 */
public class AmountFragment extends Fragment {

    /** Ported from the old MainActivity constant — not exposed as UI here, out of scope. */
    private static final String PROC_CODE = "000000";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_amount, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText amountInput = view.findViewById(R.id.amountInput);
        TextView amountError = view.findViewById(R.id.amountError);

        MainViewModel viewModel = new ViewModelProvider(requireActivity(),
                new MainViewModelFactory(((EmvEnhanceApp) requireActivity().getApplication()).getTerminal()))
                .get(MainViewModel.class);

        view.findViewById(R.id.btnConfirmAmount).setOnClickListener(v -> {
            Long amountMinor = parseAmountMinor(amountInput.getText().toString());
            if (amountMinor == null) {
                amountError.setVisibility(View.VISIBLE);
                return;
            }
            amountError.setVisibility(View.GONE);
            viewModel.acceptCard(PROC_CODE, amountMinor);
            requireActivity().finish();
        });
    }

    /**
     * Dollars-and-cents string ("10.00") to minor units (1000). Assumes a 2-decimal currency —
     * this UI is vendor-agnostic and has no access to the PAX-only {@code CurrencyConverter} the
     * kernel layer uses for the real exponent; {@link com.emvenhance.core.card.TransactionConfig}
     * only carries a minor-units {@code long} regardless.
     */
    @Nullable
    private static Long parseAmountMinor(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            double dollars = Double.parseDouble(text.trim());
            if (dollars < 0) {
                return null;
            }
            return Math.round(dollars * 100);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
