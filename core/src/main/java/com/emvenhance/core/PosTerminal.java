package com.emvenhance.core;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract POS terminal — <strong>hardware only</strong>.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Open/close readers and device initialization
 *   <li>Card search (contact / contactless / magstripe)
 *   <li>Cancel card search
 *   <li>Create vendor {@link EmvBehavior} + {@link EmvEngine}
 * </ul>
 *
 * <p>No EMV {@code handleXxx} business logic lives here. After hardware search setup, the
 * terminal delegates the full EMV lifecycle to {@link EmvBehavior#start}.
 *
 * <pre>
 *   PaxTerminal      → PaxEmvBehavior
 *   IngenicoTerminal → IngenicoEmvBehavior
 *   FakeTerminal     → FakeEmvBehavior
 * </pre>
 */
public abstract class PosTerminal implements CardReader {

    protected final EmvEngine engine;
    protected final EmvBehavior behavior;
    protected final CompositeDisposable disposables = new CompositeDisposable();

    private final AtomicBoolean searchCancelled = new AtomicBoolean(false);

    protected PosTerminal(EmvEngine engine, EmvBehavior behavior) {
        this.engine = engine;
        this.behavior = behavior;
        this.engine.attachBehavior(behavior);
        initializeVendor();
    }

    // ─── Public API ──────────────────────────────────────────────────────

    public Observable<TransactionStepEvent> transactionSteps() {
        return engine.transactionSteps();
    }

    public Observable<EmvStepEvent> emvSteps() {
        return engine.emvSteps();
    }

    public TransactionStepEvent currentState() {
        return engine.currentTransactionState();
    }

    /**
     * Starts a transaction on a background thread. Hardware search is performed inside
     * {@link EmvBehavior#start} via this {@link CardReader}.
     */
    public void startTransaction(TransactionConfig config) {
        searchCancelled.set(false);
        disposables.add(
                io.reactivex.rxjava3.core.Completable.fromAction(() -> {
                    if (!engine.begin()) {
                        return;
                    }
                    behavior.start(engine, config, this);
                })
                .subscribeOn(Schedulers.io())
                .subscribe(() -> {}, error -> {
                    //noinspection CallToPrintStackTrace
                    error.printStackTrace();
                    engine.notifyError(error.getMessage() != null
                            ? error.getMessage()
                            : "Transaction failed");
                })
        );
    }

    /** Cancels card search and in-flight EMV. */
    public void cancelTransaction() {
        searchCancelled.set(true);
        cancelCardSearch();
        engine.cancel();
    }

    public void dispose() {
        cancelTransaction();
        disposables.clear();
    }

    // ─── CardReader ──────────────────────────────────────────────────────

    @Override
    public final boolean isSearchCancelled() {
        return searchCancelled.get();
    }

    @Nullable
    @Override
    public abstract CardPresence searchCard(TransactionConfig config);

    /** Stops an in-flight {@link #searchCard} loop / reader poll. */
    protected abstract void cancelCardSearch();

    /** One-time vendor device initialization. */
    protected void initializeVendor() {
        // default: no-op
    }
}
