package com.emvenhance.ui;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.emvenhance.R;

/** Hosts {@link AmountFragment} — the amount-entry screen that starts a transaction. */
public class AmountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amount);
        setTitle(R.string.action_start_transaction);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new AmountFragment())
                    .commit();
        }
    }
}
