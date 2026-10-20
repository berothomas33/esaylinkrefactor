package com.emvenhance.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.terminal.TerminalBusyException;
import com.emvenhance.core.util.ApduTrace;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Bridges {@link PosTerminal} observables to LiveData. Activity never touches vendor code.
 */
public class MainViewModel extends ViewModel {

    private final PosTerminal terminal;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<TransactionStepEvent> transactionStep =
            new MutableLiveData<>(TransactionStepEvent.idle());
    private final MutableLiveData<TransactionStepEvent> terminalStatus =
            new MutableLiveData<>(TransactionStepEvent.idle());
    private final MutableLiveData<Event<EmvStepEvent>> emvStep = new MutableLiveData<>();
    private final MutableLiveData<String> apduTrace;
    private final MutableLiveData<Event<Boolean>> terminalBusy = new MutableLiveData<>();

    /**
     * The transactions this screen subscribed to. Disposing them cancels this screen's own, never
     * another's: a refused (busy) or finished one is already over and a dispose does nothing.
     */
    private final CompositeDisposable transactions = new CompositeDisposable();

    public MainViewModel(@NonNull PosTerminal terminal) {
        this.terminal = terminal;
        this.apduTrace = new MutableLiveData<>(terminal.apduTrace().text());

        disposables.add(terminal.transactionSteps()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(terminalStatus::setValue));

        disposables.add(terminal.emvSteps()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> emvStep.setValue(new Event<>(event))));

        // A transaction can exchange dozens of APDUs within a second; refresh the on-screen trace
        // at most every 250 ms, always ending on the latest content.
        ApduTrace trace = terminal.apduTrace();
        disposables.add(trace.changes()
                .throttleLatest(250, TimeUnit.MILLISECONDS, true)
                .map(version -> trace.text())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(apduTrace::setValue));
    }

    /** The steps of the transaction this screen started ({@link #acceptCard}), and only those. */
    public LiveData<TransactionStepEvent> getTransactionStep() {
        return transactionStep;
    }

    /**
     * Whichever transaction the terminal is on (or ended last), for a status screen that starts
     * none of its own ({@code MainActivity}).
     */
    public LiveData<TransactionStepEvent> getTerminalStatus() {
        return terminalStatus;
    }

    /** A start this screen asked for was refused: the previous transaction hasn't finished. */
    public LiveData<Event<Boolean>> getTerminalBusy() {
        return terminalBusy;
    }

    public LiveData<Event<EmvStepEvent>> getEmvStep() {
        return emvStep;
    }

    /** The current transaction's APDU trace, one APDU/step per line — see {@link ApduTrace}. */
    public LiveData<String> getApduTrace() {
        return apduTrace;
    }

    /** The APDU trace wrapped to {@code width} columns, for the receipt printer. */
    public List<String> getApduTracePrintLines(int width) {
        return terminal.apduTrace().wrapped(width);
    }

    /**
     * Preferred: accept chip / tap / swipe — vendor terminal decides. Its steps arrive on
     * {@link #getTransactionStep()}; a refused start (the POS terminal is busy with the previous
     * transaction, see {@link PosTerminal#startTransaction}) on {@link #getTerminalBusy()}.
     *
     * @param referenceNumber the original sale's reference number, for a refund; else {@code null}
     */
    public void acceptCard(TransactionType type, long amountMinor, @Nullable String referenceNumber) {
        // A screen observing from here on sees this transaction only, not the last one's outcome.
        transactionStep.setValue(TransactionStepEvent.idle());
        transactions.add(terminal.acceptCard(type, amountMinor, referenceNumber)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(transactionStep::setValue, this::onTransactionError));
    }

    private void onTransactionError(Throwable error) {
        if (error instanceof TerminalBusyException) {
            terminalBusy.setValue(new Event<>(true));
            return;
        }
        // Not expected (the terminal reports failures as ERROR steps), but never silent.
        transactionStep.setValue(TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error.getMessage() != null ? error.getMessage() : "Transaction failed")
                .build());
    }

    /** Cancels this screen's transaction; the screen closes, so it shows nothing more of it. */
    public void cancel() {
        transactions.clear();
    }

    /** Prints via the terminal-owned {@code PrinterBehavior} — see {@link PosTerminal#printReceipt}. */
    public void printReceipt(List<String> lines) {
        terminal.printReceipt(lines);
    }

    /** Switches APDU trace recording on/off — see {@link PosTerminal#setApduLoggingEnabled}. */
    public void setApduLoggingEnabled(boolean enabled) {
        terminal.setApduLoggingEnabled(enabled);
    }

    public boolean isApduLoggingEnabled() {
        return terminal.isApduLoggingEnabled();
    }

    @Override
    protected void onCleared() {
        // The screen that started the transaction is gone (Cancel, Done, or Back): don't leave its
        // search holding the reader, or its EMV running, for the next transaction.
        transactions.clear();
        disposables.clear();
        super.onCleared();
    }
}
