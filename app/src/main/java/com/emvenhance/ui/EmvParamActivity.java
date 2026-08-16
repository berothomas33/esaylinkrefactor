package com.emvenhance.ui;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.emvenhance.R;
import com.google.android.material.button.MaterialButton;

/**
 * One category at a time: tap a category button to replace the fragment container with a
 * fresh {@link EmvParamListFragment} for it. Switching categories drops the previous fragment,
 * so only the category currently on screen is ever loaded.
 */
public class EmvParamActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emv_param);
        setTitle(R.string.action_show_emv_param);

        LinearLayout categoryRow = findViewById(R.id.categoryRow);
        for (EmvParamListFragment.Category category : EmvParamListFragment.Category.values()) {
            MaterialButton button = new MaterialButton(this);
            button.setText(category.label);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            button.setLayoutParams(params);
            button.setOnClickListener(v -> showCategory(category));
            categoryRow.addView(button);
        }

        if (savedInstanceState == null) {
            showCategory(EmvParamListFragment.Category.AID);
        }
    }

    private void showCategory(EmvParamListFragment.Category category) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, EmvParamListFragment.newInstance(category))
                .commit();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
