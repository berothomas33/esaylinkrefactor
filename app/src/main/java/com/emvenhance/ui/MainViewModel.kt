package com.emvenhance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.event.EmvStepEvent
import com.emvenhance.core.terminal.PosTerminal
import com.emvenhance.core.terminal.TerminalBusyException
import com.emvenhance.core.util.ApduTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

/**
 * Bridges [PosTerminal]'s flows to the screens. Activity never touches vendor code.
 *
 * Everything here runs on `viewModelScope` (`Dispatchers.Main.immediate`); screens collect with
 * `repeatOnLifecycle(STARTED)`, so a rotation re-attaches to the same flows and neither starts
 * nor cancels a sale. The ViewModel never runs a transaction in a coroutine: it keeps the
 * [PosTerminal.Transaction] it started — its steps and its cancel.
 */
class MainViewModel(private val terminal: PosTerminal) : ViewModel() {

    /**
     * The transaction this screen started: the one thing it keeps about it. A screen collects its
     * steps ([PosTerminal.Transaction.steps], replayed from its first after a rotation) and
     * cancels it — that one only, never another's. `null`: this screen started none. A refused
     * (busy) start leaves it as it was: if this screen's own earlier sale is what keeps the
     * terminal busy, it keeps the handle to cancel it (onCleared included) — a second start never
     * drops the first one. A `StateFlow` of the handle only; the steps are never in a `StateFlow`.
     */
    private val _transaction = MutableStateFlow<PosTerminal.Transaction?>(null)
    val transaction: StateFlow<PosTerminal.Transaction?> = _transaction.asStateFlow()

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

    /** The APDU trace wrapped to `width` columns, for the receipt printer. */
    fun getApduTracePrintLines(width: Int): List<String> = terminal.apduTrace().wrapped(width)

    /**
     * Preferred: accept chip / tap / swipe — vendor terminal decides. The transaction arrives on
     * [transaction]; a refused start (the POS terminal is busy
     * with the previous transaction, see [PosTerminal.startTransaction]) on [terminalBusy].
     *
     * Called on the main thread; the transaction's busy check runs right here, before this
     * returns — a plain call, no coroutine and no `withContext`.
     *
     * @param referenceNumber the original sale's reference number, for a refund; else `null`
     */
    fun acceptCard(type: TransactionType, amountMinor: Long, referenceNumber: String?) {
        try {
            _transaction.value = terminal.acceptCard(type, amountMinor, referenceNumber)
        } catch (busy: TerminalBusyException) {
            _terminalBusy.trySend(Unit)
        }
    }

    /** Cancels this screen's transaction: CANCELLING at once, CANCELLED once stopped. */
    fun cancel() {
        _transaction.value?.cancel()
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
