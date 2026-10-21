package com.emvenhance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.event.EmvStepEvent
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.terminal.PosTerminal
import com.emvenhance.core.terminal.TerminalBusyException
import com.emvenhance.core.util.ApduTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

/**
 * Bridges [PosTerminal]'s flows to the screens. Activity never touches vendor code.
 *
 * Everything here runs on `viewModelScope` (`Dispatchers.Main.immediate`); screens collect with
 * `repeatOnLifecycle(STARTED)`, so a rotation re-attaches to the same flows and neither starts
 * nor cancels a sale. The ViewModel never runs a transaction in a coroutine: it keeps the
 * [PosTerminal.Transaction] it started, to cancel it.
 */
class MainViewModel(private val terminal: PosTerminal) : ViewModel() {

    /**
     * The terminal's transaction steps, from this ViewModel's start on (nothing from before: the
     * terminal replays no step), reset to IDLE by each [acceptCard]. Replays the latest
     * one to a screen re-attaching after rotation (as LiveData did). Not a `StateFlow`:
     * `APPROVED` and `COMPLETED` arrive back to back and a `StateFlow` could skip `APPROVED`,
     * whose data the result panel shows.
     */
    private val _transactionStep = MutableSharedFlow<TransactionStepEvent>(
        replay = 1,
        extraBufferCapacity = 64,
    ).apply { tryEmit(TransactionStepEvent.idle()) }
    val transactionStep: SharedFlow<TransactionStepEvent> = _transactionStep.asSharedFlow()

    /**
     * EMV steps as they happen; not replayed, so a screen re-attaching after rotation doesn't see
     * an old step again (what the old `Event` wrapper did for LiveData).
     */
    val emvStep: SharedFlow<EmvStepEvent> = terminal.emvSteps()

    /**
     * A start this screen asked for was refused: the previous transaction hasn't finished. One-shot
     * — delivered once, to one collector, never replayed after a rotation.
     */
    private val _terminalBusy = Channel<Unit>(Channel.CONFLATED)
    val terminalBusy: Flow<Unit> = _terminalBusy.receiveAsFlow()

    /**
     * The current transaction's APDU trace, one APDU/step per line — see [ApduTrace]. A
     * transaction can exchange dozens of APDUs within a second; this refreshes at most every
     * 250 ms, always ending on the latest content.
     */
    val apduTrace: StateFlow<String> = terminal.apduTrace().let { trace ->
        // A StateFlow is already conflated: while this waits out its 250 ms, newer versions
        // replace each other and the next round reads the latest.
        trace.changes
            .transform {
                emit(trace.text())
                delay(250)
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, trace.text())
    }

    init {
        // Unconfined: each step is copied the moment the terminal publishes it, on its thread (the
        // buffer above never waits); the screens read it on the main thread.
        viewModelScope.launch(Dispatchers.Unconfined) {
            terminal.transactionSteps().collect { _transactionStep.emit(it) }
        }
    }

    /**
     * The transaction this screen started, to cancel it — that one only, never another's. A
     * finished one ignores the cancel; a refused (busy) start leaves the earlier one here.
     */
    private var transaction: PosTerminal.Transaction? = null

    /** The APDU trace wrapped to `width` columns, for the receipt printer. */
    fun getApduTracePrintLines(width: Int): List<String> = terminal.apduTrace().wrapped(width)

    /**
     * Preferred: accept chip / tap / swipe — vendor terminal decides. Its steps arrive on
     * [transactionStep]; a refused start (the POS terminal is busy
     * with the previous transaction, see [PosTerminal.startTransaction]) on [terminalBusy].
     *
     * Called on the main thread; the transaction's busy check runs right here, before this
     * returns — a plain call, no coroutine and no `withContext`.
     *
     * @param referenceNumber the original sale's reference number, for a refund; else `null`
     */
    fun acceptCard(type: TransactionType, amountMinor: Long, referenceNumber: String?) {
        // A screen observing from here on sees this transaction only, not the last one's outcome.
        _transactionStep.tryEmit(TransactionStepEvent.idle())
        try {
            transaction = terminal.acceptCard(type, amountMinor, referenceNumber)
        } catch (busy: TerminalBusyException) {
            _terminalBusy.trySend(Unit)
        }
    }

    /** Cancels this screen's transaction; it reports ERROR "Transaction cancelled" once stopped. */
    fun cancel() {
        transaction?.cancel()
    }

    /** Prints via the terminal-owned `PrinterBehavior` — see [PosTerminal.printReceipt]. */
    fun printReceipt(lines: List<String>) {
        terminal.printReceipt(lines)
    }

    /** Switches APDU trace recording on/off — see [PosTerminal.setApduLoggingEnabled]. */
    fun setApduLoggingEnabled(enabled: Boolean) {
        terminal.setApduLoggingEnabled(enabled)
    }

    fun isApduLoggingEnabled(): Boolean = terminal.isApduLoggingEnabled()

    /**
     * The screen is gone (Cancel, Done, or Back): its transaction is cancelled, so its search
     * never keeps holding the reader, or its EMV running, for the next one.
     */
    override fun onCleared() {
        cancel()
    }
}
