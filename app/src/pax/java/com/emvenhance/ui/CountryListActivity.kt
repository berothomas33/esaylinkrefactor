package com.emvenhance.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.emvenhance.R
import com.pax.commonlib.currency.CurrencyConverter
import com.pax.configservice.export.ConfigKeyConstant
import com.pax.configservice.impl.ConfigParamService
import java.util.Currency
import java.util.Locale

/**
 * Every country/currency [CurrencyConverter] knows about, tap one to make it the terminal's
 * default — persisted to `EDC_CURRENCY_LIST` (so it survives past this process, since
 * `EmvParamService#getTerminalConfig` re-reads and re-applies that key on every call) and applied
 * immediately via [CurrencyConverter.setDefCurrency] for the current session.
 */
class CountryListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_country_list)
        setTitle(R.string.action_change_country)

        val countries = ArrayList<String>()
        CurrencyConverter.getSupportedLocaleList(countries)

        val list = findViewById<ListView>(R.id.list)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, countries)
        list.setOnItemClickListener { _, _, position, _ -> selectCountry(countries[position]) }
    }

    private fun selectCountry(countryName: String) {
        ConfigParamService().putString(ConfigKeyConstant.EDC_CURRENCY_LIST, countryName)
        CurrencyConverter.setDefCurrency(countryName)
        Toast.makeText(
            this,
            getString(R.string.country_changed, countryName, currencyCodeOf(countryName)),
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }

    private companion object {
        fun currencyCodeOf(countryName: String): String {
            for (locale in CurrencyConverter.getSupportedLocale()) {
                if (locale.getDisplayName(Locale.US) == countryName) {
                    return try {
                        Currency.getInstance(locale).currencyCode
                    } catch (e: IllegalArgumentException) {
                        "?"
                    }
                }
            }
            return "?"
        }
    }
}
