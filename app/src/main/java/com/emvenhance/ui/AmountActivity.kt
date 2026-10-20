package com.emvenhance.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.emvenhance.R

/** Hosts [AmountFragment] — the amount-entry screen that starts a transaction. */
class AmountActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_amount)
        setTitle(R.string.action_start_transaction)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AmountFragment())
                .commit()
        }
    }
}
