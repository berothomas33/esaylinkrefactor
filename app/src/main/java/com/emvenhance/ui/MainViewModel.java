package com.emvenhance.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.emvenhance.core.transaction.TransactionType;
import com.emvenhance.core.emv.EmvStepEvent;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.core.terminal.TerminalBusyException;
import com.emvenhance.core.emv.ApduTrace;
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

    private final MutableLiveData<Event<EmvStepEvent>> emvStep = new MutableLiveData<>();
    private final MutableLiveData<String> apduTrace;
    private final MutableLiveData<Event<Boolean>> terminalBusy = new MutableLiveData<>();

    /**
     * The transaction this screen started: the one thing it keeps about it. A screen reads its
     * steps ({@link PosTerminal.Transaction#steps()}, replayed from its first after a rotation)
     * and cancels it — that one only, never another's. {@code null}: this screen started none.
     * A refused (busy) start leaves it as it was: if this screen's own earlier sale is what keeps
     * the terminal busy, it keeps the handle to cancel it (onCleared included) — a second start
     * never drops the first one.
     */
    private final MutableLiveData<PosTerminal.Transaction> transaction = new MutableLiveData<>();

    public MainViewModel(@NonNull PosTerminal terminal) {
        this.terminal = terminal;
        this.apduTrace = new MutableLiveData<>(terminal.apduTrace().text());

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

    public LiveData<PosTerminal.Transaction> getTransaction() {
        return transaction;
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
     * Preferred: accept chip / tap / swipe — vendor terminal decides. The transaction arrives on
     * {@link #getTransaction()}; a refused start (the POS terminal is busy with the previous
     * transaction, see {@link PosTerminal#startTransaction}) on {@link #getTerminalBusy()}.
     *
     * @param referenceNumber the original sale's reference number, for a refund; else {@code null}
     */
    public void acceptCard(TransactionType type, long amountMinor, @Nullable String referenceNumber) {
        try {
            transaction.setValue(terminal.acceptCard(type, amountMinor, referenceNumber));
        } catch (TerminalBusyException busy) {
            terminalBusy.setValue(new Event<>(true));
        }
    }

    /** Cancels this screen's transaction: CANCELLING at once, CANCELLED once stopped. */
    public void cancel() {
        PosTerminal.Transaction current = transaction.getValue();
        if (current != null) {
            current.cancel();
        }
    }

    /** Prints via the terminal-owned {@code PrinterBehavior} — see {@link PosTerminal#printReceipt}. */
    public void printReceipt(List<String> lines) {
        terminal.printReceipt(lines);
    }

    /**
     * Switches APDU trace recording on/off ({@link ApduTrace#setEnabled}). Always off in a
     * release build: the trace holds card data.
     */
    public void setApduLoggingEnabled(boolean enabled) {
        terminal.apduTrace().setEnabled(enabled);
    }

    public boolean isApduLoggingEnabled() {
        return terminal.apduTrace().isEnabled();
    }

    @Override
    protected void onCleared() {
        // The screen that started the transaction is gone (Cancel, Done, or Back): don't leave its
        // search holding the reader, or its EMV running, for the next transaction.
        cancel();
        disposables.clear();
        super.onCleared();
    }
}
