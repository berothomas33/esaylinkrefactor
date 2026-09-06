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
import com.emvenhance.R;
import com.emvenhance.core.card.TransactionType;
import com.google.android.material.tabs.TabLayout;

/**
 * Collects the transaction type and amount, then hands off to {@link SearchCardFragment} — the
 * only entry point left into a transaction. {@link MainActivity}'s old "present card / chip only /
 * contactless only" buttons always used a hardcoded amount and are gone; this fragment is what
 * replaces them.
 */
public class AmountFragment extends Fragment {

    /** {@link TransactionType#values()} order must match the tab order in fragment_amount.xml. */
    private static final TransactionType[] TAB_ORDER = {
            TransactionType.SALE, TransactionType.CASH_IN, TransactionType.REFUND,
    };

    private TabLayout transactionTypeTabs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_amount, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        transactionTypeTabs = view.findViewById(R.id.transactionTypeTabs);
        EditText amountInput = view.findViewById(R.id.amountInput);
        TextView amountError = view.findViewById(R.id.amountError);

        view.findViewById(R.id.btnConfirmAmount).setOnClickListener(v -> {
            Long amountMinor = parseAmountMinor(amountInput.getText().toString());
            if (amountMinor == null) {
                amountError.setVisibility(View.VISIBLE);
                return;
            }
            amountError.setVisibility(View.GONE);
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer,
                            SearchCardFragment.newInstance(selectedTransactionType(), amountMinor))
                    .addToBackStack(null)
                    .commit();
        });
    }

    private TransactionType selectedTransactionType() {
        int position = transactionTypeTabs.getSelectedTabPosition();
        return position >= 0 && position < TAB_ORDER.length ? TAB_ORDER[position] : TransactionType.SALE;
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
