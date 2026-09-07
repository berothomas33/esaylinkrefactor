package com.emvenhance.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.emvenhance.R;
import com.pax.commonlib.currency.CurrencyConverter;
import com.pax.configservice.export.ConfigKeyConstant;
import com.pax.configservice.impl.ConfigParamService;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Every country/currency {@link CurrencyConverter} knows about, tap one to make it the
 * terminal's default — persisted to {@code EDC_CURRENCY_LIST} (so it survives past this
 * process, since {@code EmvParamService#getTerminalConfig} re-reads and re-applies that key on
 * every call) and applied immediately via {@link CurrencyConverter#setDefCurrency} for the
 * current session.
 */
public class CountryListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_country_list);
        setTitle(R.string.action_change_country);

        List<String> countries = new ArrayList<>();
        CurrencyConverter.getSupportedLocaleList(countries);

        ListView list = findViewById(R.id.list);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, countries));
        list.setOnItemClickListener((parent, view, position, id) -> selectCountry(countries.get(position)));
    }

    private void selectCountry(String countryName) {
        new ConfigParamService().putString(ConfigKeyConstant.EDC_CURRENCY_LIST, countryName);
        CurrencyConverter.setDefCurrency(countryName);
        Toast.makeText(this,
                getString(R.string.country_changed, countryName, currencyCodeOf(countryName)),
                Toast.LENGTH_SHORT).show();
        finish();
    }

    private static String currencyCodeOf(String countryName) {
        for (Locale locale : CurrencyConverter.getSupportedLocale()) {
            if (locale.getDisplayName(Locale.US).equals(countryName)) {
                try {
                    return Currency.getInstance(locale).getCurrencyCode();
                } catch (IllegalArgumentException e) {
                    return "?";
                }
            }
        }
        return "?";
    }
}
