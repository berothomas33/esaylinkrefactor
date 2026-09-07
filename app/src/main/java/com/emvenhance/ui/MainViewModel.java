package com.emvenhance.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.core.event.TransactionStepEvent;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;

/**
 * Bridges {@link PosTerminal} observables to LiveData. Activity never touches vendor code.
 */
public class MainViewModel extends ViewModel {

    private final PosTerminal terminal;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<TransactionStepEvent> transactionStep =
            new MutableLiveData<>(TransactionStepEvent.idle());
    private final MutableLiveData<Event<EmvStepEvent>> emvStep = new MutableLiveData<>();

    public MainViewModel(@NonNull PosTerminal terminal) {
        this.terminal = terminal;

        disposables.add(terminal.transactionSteps()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(transactionStep::setValue));

        disposables.add(terminal.emvSteps()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> emvStep.setValue(new Event<>(event))));
    }

    public LiveData<TransactionStepEvent> getTransactionStep() {
        return transactionStep;
    }

    public LiveData<Event<EmvStepEvent>> getEmvStep() {
        return emvStep;
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
