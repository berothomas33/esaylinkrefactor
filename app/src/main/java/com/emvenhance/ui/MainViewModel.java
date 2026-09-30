package com.emvenhance.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.core.event.TransactionStepEvent;
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
    private final MutableLiveData<Event<EmvStepEvent>> emvStep = new MutableLiveData<>();
    private final MutableLiveData<String> apduTrace = new MutableLiveData<>(ApduTrace.text());

    public MainViewModel(@NonNull PosTerminal terminal) {
        this.terminal = terminal;

        disposables.add(terminal.transactionSteps()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(transactionStep::setValue));

        disposables.add(terminal.emvSteps()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> emvStep.setValue(new Event<>(event))));

        // A transaction can exchange dozens of APDUs within a second; refresh the on-screen trace
        // at most every 250 ms, always ending on the latest content.
        disposables.add(ApduTrace.changes()
                .throttleLatest(250, TimeUnit.MILLISECONDS, true)
                .map(version -> ApduTrace.text())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(apduTrace::setValue));
    }

    public LiveData<TransactionStepEvent> getTransactionStep() {
        return transactionStep;
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
        return ApduTrace.wrapped(width);
    }

    /** Preferred: accept chip / tap / swipe — vendor terminal decides. */
    public void acceptCard(TransactionType type, long amountMinor) {
        terminal.acceptCard(type, amountMinor);
    }

    public void cancel() {
        terminal.cancelTransaction();
    }

    /** Prints via the terminal-owned {@code PrinterBehavior} — see {@link PosTerminal#printReceipt}. */
    public void printReceipt(List<String> lines) {
        terminal.printReceipt(lines);
    }

    /** No-op on vendors with no real APDU trans log — see {@link PosTerminal#setApduLoggingEnabled}. */
    public void setApduLoggingEnabled(boolean enabled) {
        terminal.setApduLoggingEnabled(enabled);
    }

    public boolean isApduLoggingEnabled() {
        return terminal.isApduLoggingEnabled();
    }

    @Override
    protected void onCleared() {
        disposables.clear();
        super.onCleared();
    }
}
