package com.emvenhance.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.emvenhance.R
import com.emvenhance.core.card.TransactionType
import com.google.android.material.tabs.TabLayout

/**
 * Collects the transaction type and amount, then hands off to [SearchCardFragment] — the only
 * entry point left into a transaction. [MainActivity]'s old "present card / chip only /
 * contactless only" buttons always used a hardcoded amount and are gone; this fragment is what
 * replaces them.
 */
class AmountFragment : Fragment() {

    private lateinit var transactionTypeTabs: TabLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_amount, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        transactionTypeTabs = view.findViewById(R.id.transactionTypeTabs)
        val amountInput = view.findViewById<EditText>(R.id.amountInput)
        val amountError = view.findViewById<TextView>(R.id.amountError)
        val referenceGroup = view.findViewById<View>(R.id.referenceGroup)
        val referenceInput = view.findViewById<EditText>(R.id.referenceInput)
        val referenceError = view.findViewById<TextView>(R.id.referenceError)

        // A refund needs the original sale's reference number (the host's refund request).
        transactionTypeTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                referenceGroup.visibility =
                    if (selectedTransactionType() == TransactionType.REFUND) View.VISIBLE else View.GONE
                referenceError.visibility = View.GONE
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        view.findViewById<View>(R.id.btnConfirmAmount).setOnClickListener {
            val amountMinor = parseAmountMinor(amountInput.text.toString())
            if (amountMinor == null) {
                amountError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            amountError.visibility = View.GONE
            val type = selectedTransactionType()
            var referenceNumber: String? = null
            if (type == TransactionType.REFUND) {
                referenceNumber = referenceInput.text.toString().trim()
                if (referenceNumber.isEmpty()) {
                    referenceError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
            }
            referenceError.visibility = View.GONE
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SearchCardFragment.newInstance(type, amountMinor, referenceNumber))
                .addToBackStack(null)
                .commit()
        }
    }

    private fun selectedTransactionType(): TransactionType {
        val position = transactionTypeTabs.selectedTabPosition
        return if (position >= 0 && position < TAB_ORDER.size) TAB_ORDER[position] else TransactionType.SALE
    }

    private companion object {
        /** [TransactionType.values] order must match the tab order in fragment_amount.xml. */
        val TAB_ORDER = arrayOf(TransactionType.SALE, TransactionType.CASH_IN, TransactionType.REFUND)

        /**
         * Dollars-and-cents string ("10.00") to minor units (1000). Assumes a 2-decimal currency —
         * this UI is vendor-agnostic and has no access to the PAX-only `CurrencyConverter` the
         * kernel layer uses for the real exponent; [com.emvenhance.core.card.TransactionConfig]
         * only carries a minor-units `long` regardless.
         */
        fun parseAmountMinor(text: String?): Long? {
            if (text == null || text.trim().isEmpty()) {
                return null
            }
            return try {
                val dollars = text.trim().toDouble()
                if (dollars < 0) null else Math.round(dollars * 100)
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
