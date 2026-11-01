package com.emvenhance.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.emvenhance.EmvEnhanceApp;
import com.emvenhance.R;
import com.emvenhance.core.terminal.PosTerminal;
import java.util.List;

public class CountryListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_country_list);
        setTitle(R.string.action_change_country);

        PosTerminal terminal = ((EmvEnhanceApp) getApplication()).getTerminal();
        List<String> countries = terminal.countries();

        ListView list = findViewById(R.id.list);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, countries));
        list.setOnItemClickListener((parent, view, position, id) -> {
            String country = countries.get(position);
            String currency = terminal.setCountry(country);
            Toast.makeText(this, getString(R.string.country_changed, country, currency),
                    Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
