package com.emvenhance.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.emvenhance.core.EmvStepEvent;
import com.emvenhance.core.PosTerminal;
import com.emvenhance.core.TransactionConfig;
import com.emvenhance.core.TransactionStepEvent;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Subscribes to the two subjects exposed by {@link PosTerminal} and re-publishes
 * them as {@link LiveData} for the Activity.
 *
 * <p>The Activity never touches RxJava, the PosTerminal, or the engine. It observes
 * two LiveData fields and forwards user clicks as method calls.
 *
 * <p>The transaction-step LiveData is plain (sticky) because a rotated Activity should
 * immediately see "Approved" if that's where the transaction is. The EMV-step LiveData
 * is wrapped in {@link Event} (consume-once) because those are progress pings that should
 * not replay after rotation.
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

    /** High-level lifecycle — sticky, safe for rotation. */
    public LiveData<TransactionStepEvent> getTransactionStep() {
        return transactionStep;
    }

    /** Fine-grained kernel progress — consume-once, not replayed after rotation. */
    public LiveData<Event<EmvStepEvent>> getEmvStep() {
        return emvStep;
    }

    public void startContact(String procCode, long amountMinor) {
        terminal.startTransaction(
                new TransactionConfig(procCode, amountMinor, TransactionConfig.Mode.CONTACT));
    }

    public void startContactless(String procCode, long amountMinor) {
        terminal.startTransaction(
                new TransactionConfig(procCode, amountMinor, TransactionConfig.Mode.CONTACTLESS));
    }

    @Override
    protected void onCleared() {
        disposables.clear();
        super.onCleared();
    }
}
