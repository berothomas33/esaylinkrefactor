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
import com.emvenhance.core.transaction.TransactionType;
import com.google.android.material.tabs.TabLayout;

public class AmountFragment extends Fragment {

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
        View referenceGroup = view.findViewById(R.id.referenceGroup);
        EditText referenceInput = view.findViewById(R.id.referenceInput);
        TextView referenceError = view.findViewById(R.id.referenceError);

        transactionTypeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                referenceGroup.setVisibility(selectedTransactionType() == TransactionType.REFUND
                        ? View.VISIBLE : View.GONE);
                referenceError.setVisibility(View.GONE);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        view.findViewById(R.id.btnConfirmAmount).setOnClickListener(v -> {
            Long amountMinor = parseAmountMinor(amountInput.getText().toString());
            if (amountMinor == null) {
                amountError.setVisibility(View.VISIBLE);
                return;
            }
            amountError.setVisibility(View.GONE);
            TransactionType type = selectedTransactionType();
            String referenceNumber = null;
            if (type == TransactionType.REFUND) {
                referenceNumber = referenceInput.getText().toString().trim();
                if (referenceNumber.isEmpty()) {
                    referenceError.setVisibility(View.VISIBLE);
                    return;
                }
            }
            referenceError.setVisibility(View.GONE);
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer,
                            SearchCardFragment.newInstance(type, amountMinor, referenceNumber))
                    .addToBackStack(null)
                    .commit();
        });
    }

    private TransactionType selectedTransactionType() {
        int position = transactionTypeTabs.getSelectedTabPosition();
        return position >= 0 && position < TAB_ORDER.length ? TAB_ORDER[position] : TransactionType.SALE;
    }

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
