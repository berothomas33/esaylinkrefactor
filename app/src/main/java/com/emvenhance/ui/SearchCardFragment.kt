package com.emvenhance.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.emvenhance.BuildConfig
import com.emvenhance.EmvEnhanceApp
import com.emvenhance.R
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Shown once the amount is confirmed: a live [com.emvenhance.core.event.EmvStep] banner plus the
 * accepted entry methods, while [MainViewModel.acceptCard] searches.
 *
 * The three method rows are informational, not separately actionable — `acceptCard` already
 * searches mag/chip/contactless together (entry method `ANY`); tapping a physical card of any of
 * those kinds is what actually matters. Manual Entry is the one real exception, since a reader
 * can't detect "the operator wants to type a PAN" on its own — that flow doesn't exist in this
 * app yet, so the button is a stub for now.
 *
 * Every flow is collected with `repeatOnLifecycle(STARTED)` on the view's lifecycle: a rotation
 * re-attaches to the same [MainViewModel] (activity-scoped), which keeps its transaction.
 */
class SearchCardFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory((requireActivity().application as EmvEnhanceApp).terminal)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_search_card, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        val type = TransactionType.valueOf(
            args.getString(ARG_TRANSACTION_TYPE, TransactionType.SALE.name),
        )
        val amountMinor = args.getLong(ARG_AMOUNT_MINOR, 0)
        val referenceNumber = args.getString(ARG_REFERENCE_NUMBER)

        view.findViewById<TextView>(R.id.amountText).text = formatAmount(amountMinor)

        val emvStepBanner = view.findViewById<TextView>(R.id.emvStepBanner)

        bindMethodRow(
            view, R.id.rowMagstripe, "MAG",
            getString(R.string.method_magstripe_title), getString(R.string.method_magstripe_subtitle),
        )
        bindMethodRow(
            view, R.id.rowChip, "CHIP",
            getString(R.string.method_chip_title), getString(R.string.method_chip_subtitle),
        )
        bindMethodRow(
            view, R.id.rowContactless, "NFC",
            getString(R.string.method_contactless_title), getString(R.string.method_contactless_subtitle),
        )

        view.findViewById<View>(R.id.btnManualEntry).setOnClickListener {
            Toast.makeText(requireContext(), R.string.manual_entry_not_implemented, Toast.LENGTH_SHORT).show()
        }

        val btnCancelSearch = view.findViewById<View>(R.id.btnCancelSearch)
        btnCancelSearch.setOnClickListener {
            viewModel.cancel()
            requireActivity().finish()
        }

        val outcomeGroup = view.findViewById<View>(R.id.outcomeGroup)
        view.findViewById<View>(R.id.btnOutcomeDone).setOnClickListener { requireActivity().finish() }
        view.findViewById<View>(R.id.btnOutcomeRetry).setOnClickListener {
            outcomeGroup.visibility = View.GONE
            viewModel.acceptCard(type, amountMinor, referenceNumber)
        }

        bindKvRow(view, R.id.rowPan, getString(R.string.label_pan_result))
        bindKvRow(view, R.id.rowTvr, getString(R.string.label_tvr))
        bindKvRow(view, R.id.rowTacDenial, getString(R.string.label_tac_denial))
        bindKvRow(view, R.id.rowIacDenial, getString(R.string.label_iac_denial))
        bindKvRow(view, R.id.rowIccData, getString(R.string.label_icc_data))

        val traceText = bindApduTrace(view)

        val resultGroup = view.findViewById<View>(R.id.resultGroup)
        view.findViewById<View>(R.id.btnPrintResult).setOnClickListener {
            val lines = buildResultLines(view)
            val trace = viewModel.getApduTracePrintLines(PRINT_WIDTH)
            if (trace.isNotEmpty()) {
                lines.add("")
                lines.addAll(trace)
            }
            viewModel.printReceipt(lines)
            Toast.makeText(requireContext(), R.string.result_printed, Toast.LENGTH_SHORT).show()
        }

        // Once a card is actually detected, the "insert / swipe / tap / manual" choices no
        // longer describe what's happening — hide them instead of leaving a stale, misleading
        // prompt on screen while EMV runs (tracked by emvStepBanner above instead). The result
        // panel is the mirror image: hidden until the transaction actually finishes, chip/
        // contactless only — mag/manual have no TVR/TAC/IAC/Field 55 to show (§ PaxEmvBehavior
        // captureTransactionSummary), so their rows just read "—".
        val methodSelectionGroup = view.findViewById<View>(R.id.methodSelectionGroup)
        val retryPromptText = view.findViewById<TextView>(R.id.retryPromptText)

        fun render(event: TransactionStepEvent) {
            val step = event.step
            val stillChoosing = step == TransactionStep.IDLE ||
                step == TransactionStep.TRANSACTION_STARTED ||
                step == TransactionStep.WAITING_FOR_CARD
            methodSelectionGroup.visibility = if (stillChoosing) View.VISIBLE else View.GONE

            // A retry (tap too short, see phone, chip not read...) brings the card UI back with
            // the reason on top, scrolled into view — the screen may be scrolled down to the
            // APDU trace or result from the attempt that just failed.
            if (step == TransactionStep.WAITING_FOR_CARD) {
                val prompt = event.get<Any>(TransactionStepEvent.KEY_RETRY_PROMPT)
                retryPromptText.text = prompt?.toString() ?: ""
                retryPromptText.visibility = if (prompt != null) View.VISIBLE else View.GONE
                if (prompt != null && view is ScrollView) {
                    view.post { view.smoothScrollTo(0, 0) }
                }
            }

            // Final outcome. A card-search timeout, reader failure, kernel/PIN error or host
            // failure all end in ERROR (never followed by COMPLETED); a decline ends in DECLINED
            // — the cardholder sees both as a declined transaction, with the reason under it.
            when (step) {
                TransactionStep.APPROVED -> {
                    showOutcome(view, true, event.get<Any>(TransactionStepEvent.KEY_RESULT))
                    btnCancelSearch.visibility = View.GONE
                }
                TransactionStep.DECLINED -> {
                    showOutcome(view, false, event.get<Any>(TransactionStepEvent.KEY_ERROR))
                    btnCancelSearch.visibility = View.GONE
                }
                TransactionStep.ERROR, TransactionStep.CANCELLED -> {
                    showOutcome(view, false, event.message ?: event.get<Any>(TransactionStepEvent.KEY_ERROR))
                    btnCancelSearch.visibility = View.GONE
                }
                TransactionStep.TRANSACTION_STARTED -> {
                    outcomeGroup.visibility = View.GONE
                    btnCancelSearch.visibility = View.VISIBLE
                }
                else -> Unit
            }

            // APPROVED/DECLINED is immediately followed by a separate COMPLETED event (engine's
            // notifyCompleted(), fired right after) — both are collected here within the same
            // main-thread burst, before a frame ever draws. Hiding resultGroup again on that
            // COMPLETED event (or on ERROR, or on anything else) meant the panel was set VISIBLE
            // then GONE before Android ever painted it — never actually seen. Only ever hide it
            // when a *new* transaction starts; every other step leaves it as it is.
            if (step == TransactionStep.APPROVED || step == TransactionStep.DECLINED) {
                resultGroup.visibility = View.VISIBLE
                setKvValue(view, R.id.rowPan, event.getString(TransactionStepEvent.KEY_PAN))
                setKvValue(view, R.id.rowTvr, event.getString(TransactionStepEvent.KEY_TVR))
                setKvValue(view, R.id.rowTacDenial, event.getString(TransactionStepEvent.KEY_TAC_DENIAL))
                setKvValue(view, R.id.rowIacDenial, event.getString(TransactionStepEvent.KEY_IAC_DENIAL))
                setKvValue(view, R.id.rowIccData, event.getString(TransactionStepEvent.KEY_ICC_DATA))
            } else if (stillChoosing) {
                resultGroup.visibility = View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.emvStep.collect { emvStepBanner.text = it.toString() } }
                // This screen's transaction: its own steps, so nothing of the previous one shows,
                // and all of them again after a rotation (the result panel comes back with
                // APPROVED's data). None (a refused start): the card choices, idle.
                launch {
                    viewModel.transaction.collectLatest { transaction ->
                        if (transaction == null) {
                            render(TransactionStepEvent.idle())
                        } else {
                            transaction.steps().collect { render(it) }
                        }
                    }
                }
                // After the transaction collector: a refused start shows idle, then BUSY — drawn last.
                launch { viewModel.terminalBusy.collect { showBusy(view) } }
                if (traceText != null) {
                    launch { viewModel.apduTrace.collect { traceText.setText(it) } }
                }
            }
        }

        // Fire the search the moment this screen is up, so the reader is live as soon as the
        // cardholder sees "present card" — matches AmountFragment's old behavior, just moved
        // here now that there's a dedicated screen for it. Only the first time: a rotation
        // recreates this view while its transaction carries on.
        if (savedInstanceState == null) {
            viewModel.acceptCard(type, amountMinor, referenceNumber)
        }
    }

    /**
     * APDU trace card: debug builds only (the trace holds card data and is never recorded in a
     * release build). The EditText is filled live, with the soft keyboard suppressed, so it can be
     * scrolled, selected and copied — over Vysor too, or with the Copy button.
     *
     * @return the trace's EditText to fill, or `null` in a release build
     */
    private fun bindApduTrace(view: View): EditText? {
        val group = view.findViewById<View>(R.id.apduTraceGroup)
        if (!BuildConfig.DEBUG) {
            group.visibility = View.GONE
            return null
        }

        val chkApduLog = view.findViewById<MaterialCheckBox>(R.id.chkApduLog)
        chkApduLog.isChecked = viewModel.isApduLoggingEnabled()
        chkApduLog.setOnCheckedChangeListener { _, checked -> viewModel.setApduLoggingEnabled(checked) }

        val traceText = view.findViewById<EditText>(R.id.apduTraceText)
        traceText.showSoftInputOnFocus = false

        view.findViewById<View>(R.id.btnCopyApdu).setOnClickListener {
            val trace = traceText.text.toString()
            if (trace.isEmpty()) {
                Toast.makeText(requireContext(), R.string.apdu_trace_nothing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("APDU trace", trace))
            Toast.makeText(requireContext(), R.string.apdu_trace_copied, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnPrintApdu).setOnClickListener {
            val trace = viewModel.getApduTracePrintLines(PRINT_WIDTH)
            if (trace.isEmpty()) {
                Toast.makeText(requireContext(), R.string.apdu_trace_nothing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.printReceipt(trace)
            Toast.makeText(requireContext(), R.string.result_printed, Toast.LENGTH_SHORT).show()
        }
        return traceText
    }

    companion object {
        private const val ARG_TRANSACTION_TYPE = "transactionType"
        private const val ARG_AMOUNT_MINOR = "amountMinor"
        private const val ARG_REFERENCE_NUMBER = "referenceNumber"

        /** Characters per printed line: 384-dot head with PaxPrinter's 12x24 font. */
        private const val PRINT_WIDTH = 32

        /** @param referenceNumber the original sale's reference number, for a refund; else `null` */
        @JvmStatic
        fun newInstance(type: TransactionType, amountMinor: Long, referenceNumber: String?): SearchCardFragment {
            val args = Bundle()
            args.putString(ARG_TRANSACTION_TYPE, type.name)
            args.putLong(ARG_AMOUNT_MINOR, amountMinor)
            args.putString(ARG_REFERENCE_NUMBER, referenceNumber)
            val fragment = SearchCardFragment()
            fragment.arguments = args
            return fragment
        }

        private fun showOutcome(view: View, approved: Boolean, reason: Any?) {
            val group = view.findViewById<View>(R.id.outcomeGroup)
            group.setBackgroundColor(if (approved) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
            view.findViewById<TextView>(R.id.outcomeTitle)
                .setText(if (approved) R.string.outcome_approved else R.string.outcome_declined)
            val reasonText = view.findViewById<TextView>(R.id.outcomeReason)
            reasonText.text = reason?.toString() ?: ""
            reasonText.visibility = if (reason != null) View.VISIBLE else View.GONE
            // Retry only makes sense after a failure; an approved sale is finished.
            view.findViewById<View>(R.id.btnOutcomeRetry).visibility = if (approved) View.GONE else View.VISIBLE
            group.visibility = View.VISIBLE

            val banner = view.findViewById<TextView>(R.id.emvStepBanner)
            banner.setText(if (approved) R.string.outcome_approved else R.string.outcome_declined)
        }

        /**
         * This screen's start was refused: the previous transaction hasn't finished with the
         * reader yet (usually a cancelled search still stopping). Nothing of this screen's runs,
         * so nothing to cancel — Try again, or Done.
         */
        private fun showBusy(view: View) {
            showOutcome(view, false, view.context.getString(R.string.terminal_busy_reason))
            view.findViewById<TextView>(R.id.outcomeTitle).setText(R.string.outcome_terminal_busy)
            view.findViewById<TextView>(R.id.emvStepBanner).setText(R.string.outcome_terminal_busy)
            view.findViewById<View>(R.id.methodSelectionGroup).visibility = View.GONE
            view.findViewById<View>(R.id.btnCancelSearch).visibility = View.GONE
        }

        private fun bindMethodRow(parent: View, rowId: Int, badge: String, title: String, subtitle: String) {
            val row = parent.findViewById<View>(rowId)
            row.findViewById<TextView>(R.id.methodBadge).text = badge
            row.findViewById<TextView>(R.id.methodTitle).text = title
            row.findViewById<TextView>(R.id.methodSubtitle).text = subtitle
        }

        private fun bindKvRow(parent: View, rowId: Int, label: String) {
            parent.findViewById<View>(rowId).findViewById<TextView>(R.id.kvLabel).text = label
        }

        private fun setKvValue(parent: View, rowId: Int, value: String) {
            parent.findViewById<View>(rowId).findViewById<TextView>(R.id.kvValue).text = value
        }

        /** Reads back what's currently on screen — the same values the result rows just set. */
        private fun buildResultLines(view: View): MutableList<String> = mutableListOf(
            kvLine(view, R.id.rowPan),
            kvLine(view, R.id.rowTvr),
            kvLine(view, R.id.rowTacDenial),
            kvLine(view, R.id.rowIacDenial),
            kvLine(view, R.id.rowIccData),
        )

        private fun kvLine(parent: View, rowId: Int): String {
            val row = parent.findViewById<View>(rowId)
            val label = row.findViewById<TextView>(R.id.kvLabel).text.toString()
            val value = row.findViewById<TextView>(R.id.kvValue).text.toString()
            return "$label: $value"
        }

        private fun formatAmount(amountMinor: Long): String {
            val format = NumberFormat.getNumberInstance(Locale.US)
            format.minimumFractionDigits = 2
            format.maximumFractionDigits = 2
            return format.format(amountMinor / 100.0)
        }
    }
}
