package com.emvenhance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.event.EmvStepEvent
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.terminal.PosTerminal
import com.emvenhance.core.terminal.TerminalBusyException
import com.emvenhance.core.util.ApduTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
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
 * nor cancels a sale.
 */
class MainViewModel(private val terminal: PosTerminal) : ViewModel() {

    /**
     * The steps of the transaction this screen started ([acceptCard]), and only those. Replays the
     * latest one to a screen re-attaching after rotation (as LiveData did). Not a `StateFlow`:
     * `APPROVED` and `COMPLETED` arrive back to back and a `StateFlow` could skip `APPROVED`,
     * whose data the result panel shows.
     */
    private val _transactionStep = MutableSharedFlow<TransactionStepEvent>(
        replay = 1,
        extraBufferCapacity = 64,
    ).apply { tryEmit(TransactionStepEvent.idle()) }
    val transactionStep: SharedFlow<TransactionStepEvent> = _transactionStep.asSharedFlow()

    /**
     * Whichever transaction the terminal is on (or ended last), for a status screen that starts
     * none of its own (`MainActivity`). Replays the latest step.
     */
    val terminalStatus: SharedFlow<TransactionStepEvent> = terminal.transactionSteps()

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

    /**
     * The transactions this screen collects. Cancelling them cancels this screen's own, never
     * another's: a refused (busy) or finished one is already over and a cancel does nothing.
     */
    private val transactions = mutableListOf<Job>()

    /** The APDU trace wrapped to `width` columns, for the receipt printer. */
    fun getApduTracePrintLines(width: Int): List<String> = terminal.apduTrace().wrapped(width)

    /**
     * Preferred: accept chip / tap / swipe — vendor terminal decides. Its steps arrive on
     * [transactionStep]; a refused start (the POS terminal is busy with the previous transaction,
     * see [PosTerminal.startTransaction]) on [terminalBusy].
     *
     * Called on the main thread; the transaction's busy check runs right here, before this
     * returns (`Main.immediate`, no `flowOn`).
     *
     * @param referenceNumber the original sale's reference number, for a refund; else `null`
     */
    fun acceptCard(type: TransactionType, amountMinor: Long, referenceNumber: String?) {
        // A screen observing from here on sees this transaction only, not the last one's outcome.
        _transactionStep.tryEmit(TransactionStepEvent.idle())
        transactions += viewModelScope.launch {
            terminal.acceptCard(type, amountMinor, referenceNumber)
                .catch { error -> onTransactionError(error) }
                .collect { _transactionStep.emit(it) }
        }
    }

    private suspend fun onTransactionError(error: Throwable) {
        if (error is TerminalBusyException) {
            _terminalBusy.send(Unit)
            return
        }
        // Not expected (the terminal reports failures as ERROR steps), but never silent.
        _transactionStep.emit(
            TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error.message ?: "Transaction failed")
                .build(),
        )
    }

    /** Cancels this screen's transaction; the screen closes, so it shows nothing more of it. */
    fun cancel() {
        transactions.forEach { it.cancel() }
        transactions.clear()
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

    // onCleared: viewModelScope cancels every transaction this screen started (Cancel, Done, or
    // Back), so its search never keeps holding the reader, or its EMV running, for the next one.
}
