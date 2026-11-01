package com.emvenhance.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.emvenhance.EmvEnhanceApp;
import com.emvenhance.R;
import com.emvenhance.core.param.EmvParams;
import java.util.ArrayList;
import java.util.List;

public class EmvParamListFragment extends Fragment {

    private static final String ARG_CATEGORY = "category";
    private static final String ARG_FROM_CACHE = "fromCache";

    public static EmvParamListFragment newInstance(String category, boolean fromCache) {
        EmvParamListFragment fragment = new EmvParamListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, category);
        args.putBoolean(ARG_FROM_CACHE, fromCache);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_emv_param_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EmvParams params = ((EmvEnhanceApp) requireActivity().getApplication()).getTerminal().emvParams();
        Bundle args = getArguments();
        String category = args != null ? args.getString(ARG_CATEGORY) : null;
        boolean fromCache = args != null && args.getBoolean(ARG_FROM_CACHE);

        ProgressBar progress = view.findViewById(R.id.progress);
        ListView list = view.findViewById(R.id.list);
        TextView emptyText = view.findViewById(R.id.emptyText);

        List<EmvParams.Row> rows = new ArrayList<>();
        list.setOnItemClickListener((parent, row, position, id) -> showDetails(rows.get(position)));

        new Thread(() -> {
            List<String> titles = new ArrayList<>();
            String failure = null;
            try {
                List<EmvParams.Row> loaded = fromCache
                        ? params.cachedRows(category) : params.rows(category);
                if (loaded == null) {
                    titles = null;
                } else {
                    for (EmvParams.Row row : loaded) {
                        rows.add(row);
                        titles.add(row.title());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                failure = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            List<String> finalTitles = titles;
            String finalFailure = failure;
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                if (finalFailure != null) {
                    emptyText.setText(getString(R.string.emv_param_load_failed) + "\n" + finalFailure);
                    emptyText.setVisibility(View.VISIBLE);
                } else if (finalTitles == null) {
                    emptyText.setText(R.string.emv_param_cache_empty);
                    emptyText.setVisibility(View.VISIBLE);
                } else if (finalTitles.isEmpty()) {
                    emptyText.setText(R.string.emv_param_empty);
                    emptyText.setVisibility(View.VISIBLE);
                } else {
                    list.setAdapter(new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_list_item_1, finalTitles));
                }
            });
        }, "EmvParamList-" + category).start();
    }

    private void showDetails(EmvParams.Row row) {
        new Thread(() -> {
            String text;
            try {
                text = row.details();
            } catch (Exception e) {
                text = String.valueOf(e);
            }
            String finalText = text;
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                TextView view = new TextView(requireContext());
                view.setText(finalText);
                view.setTextIsSelectable(true);
                view.setTypeface(Typeface.MONOSPACE);
                view.setTextSize(11);
                int pad = (int) (12 * getResources().getDisplayMetrics().density);
                view.setPadding(pad, pad, pad, pad);
                ScrollView scroll = new ScrollView(requireContext());
                scroll.addView(view);
                new AlertDialog.Builder(requireContext())
                        .setTitle(row.title())
                        .setView(scroll)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        }, "EmvParamDetails").start();
    }
}
