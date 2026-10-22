package com.emvenhance.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.emvenhance.EmvEnhanceApp
import com.emvenhance.R

/**
 * Every country the terminal can be set to (`PosTerminal.countries()`); tap one to make it the
 * terminal's country and currency (`PosTerminal.setCountry`).
 */
class CountryListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_country_list)
        setTitle(R.string.action_change_country)

        val terminal = (application as EmvEnhanceApp).terminal
        val countries = terminal.countries()

        val list = findViewById<ListView>(R.id.list)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, countries)
        list.setOnItemClickListener { _, _, position, _ ->
            val country = countries[position]
            val currency = terminal.setCountry(country)
            Toast.makeText(this, getString(R.string.country_changed, country, currency), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
