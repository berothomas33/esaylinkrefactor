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

public class MainViewModel extends ViewModel {

    private final PosTerminal terminal;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<Event<EmvStepEvent>> emvStep = new MutableLiveData<>();
    private final MutableLiveData<String> apduTrace;
    private final MutableLiveData<Event<Boolean>> terminalBusy = new MutableLiveData<>();

    private final MutableLiveData<PosTerminal.Transaction> transaction = new MutableLiveData<>();

    public MainViewModel(@NonNull PosTerminal terminal) {
        this.terminal = terminal;
        this.apduTrace = new MutableLiveData<>(terminal.apduTrace().text());

        disposables.add(terminal.emvSteps()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> emvStep.setValue(new Event<>(event))));

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

    public LiveData<Event<Boolean>> getTerminalBusy() {
        return terminalBusy;
    }

    public LiveData<Event<EmvStepEvent>> getEmvStep() {
        return emvStep;
    }

    public LiveData<String> getApduTrace() {
        return apduTrace;
    }

    public List<String> getApduTracePrintLines(int width) {
        return terminal.apduTrace().wrapped(width);
    }

    public void acceptCard(TransactionType type, long amountMinor, @Nullable String referenceNumber) {
        try {
            transaction.setValue(terminal.acceptCard(type, amountMinor, referenceNumber));
        } catch (TerminalBusyException busy) {
            terminalBusy.setValue(new Event<>(true));
        }
    }

    public void cancel() {
        PosTerminal.Transaction current = transaction.getValue();
        if (current != null) {
            current.cancel();
        }
    }

    public void printReceipt(List<String> lines) {
        terminal.printReceipt(lines);
    }

    public void setApduLoggingEnabled(boolean enabled) {
        terminal.setApduLoggingEnabled(enabled);
    }

    public boolean isApduLoggingEnabled() {
        return terminal.isApduLoggingEnabled();
    }

    @Override
    protected void onCleared() {
        cancel();
        disposables.clear();
        super.onCleared();
    }
}
