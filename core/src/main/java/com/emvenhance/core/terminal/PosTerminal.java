package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.util.ApduTrace;
import com.emvenhance.core.util.EmvLog;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The only public hardware API for the app.
 *
 * <p>Owns card search, host communication, and printer. EMV after entry selection is
 * delegated to its {@link EmvEngine}, which owns the vendor's {@link EmvBehavior}.
 *
 * <pre>
 *   UI → PosTerminal.startTransaction(ANY|CHIP|…).subscribe()   [returns immediately]
 *          └── engine.prepare + searchCard(config, listener)        ← vendor SDK
 *                 └── listener.onXxxDetected(card)                 ← vendor callback
 *                        └── engine.start(card)                     ← vendor EMV, from inside the callback
 *                               └── behavior → engine.authorize → PosTerminal.authorize → host
 * </pre>
 *
 * <p>A transaction is an {@link Observable} of its own {@link TransactionStepEvent}s: subscribing
 * starts it, disposing cancels it (that one only, never another's), and {@code onComplete} means
 * the terminal is free again. The transaction thread stays inside this class.
 */
public abstract class PosTerminal {

    protected final EmvEngine engine;
    protected final CommunicationBehavior communication;
    protected final PrinterBehavior printer;

    /**
     * The one thread every transaction runs on, from card search through EMV and the host: one
     * reader, one kernel. Nothing ever queues on it — a subscribe to {@link #startTransaction}
     * is refused while the previous transaction is still there.
     */
    private final Scheduler transactionScheduler =
            Schedulers.from(Executors.newSingleThreadExecutor());

    /**
     * The one thread receipts print on: one printer, one job at a time, in the order they were
     * asked for — two quick taps on Print queue the second receipt instead of driving the printer
     * from two threads at once.
     */
    private final Scheduler printScheduler =
            Schedulers.from(Executors.newSingleThreadExecutor());

    /**
     * The transaction on {@link #transactionScheduler}, from its accepted start until it is done
     * with the reader; empty when the terminal is free. Only it may report to the engine.
     */
    private final AtomicReference<Transaction> current = new AtomicReference<>();

    /**
     * @param behavior the vendor's EMV — handed to this terminal's {@link EmvEngine}, which owns
     *                 it; the terminal itself only ever talks to the engine
     */
    protected PosTerminal(EmvBehavior behavior,
            CommunicationBehavior communication,
            PrinterBehavior printer) {
        this.communication = communication;
        this.printer = printer;
        this.engine = new EmvEngine(behavior, this::authorize);
        initializeVendor();
    }

    public CommunicationBehavior communication() {
        return communication;
    }

    public PrinterBehavior printer() {
        return printer;
    }

    /**
     * Host authorize via the terminal-owned {@link CommunicationBehavior}. This is also where a
     * vendor's {@link EmvBehavior} goes online — {@link EmvEngine#authorize} hands its request
     * here — so it's one implementation, whether it's the terminal or a vendor asking.
     *
     * <p>Publishes {@code ONLINE_REQUIRED} / {@code ONLINE_PROCESSING} / {@code ONLINE_COMPLETED}
     * around the call, so a vendor gets the notify sequence for free instead of repeating it.
     */
    public AuthResult authorize(TransactionConfig config) {
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));
        AuthResult result;
        try {
            // Cancel doesn't cut this short: once sent, the sale may be approved at the host, and
            // with no reversal yet the terminal waits for that answer — or the timeout.
            result = communication.authorize(config)
                    .timeout(onlineTimeoutMs(), TimeUnit.MILLISECONDS)
                    .blockingGet();
        } catch (Exception e) {
            // No usable host answer (network, timeout, envelope error): online failed, not a
            // host decline.
            result = AuthResult.failed(e.getCause() instanceof TimeoutException
                    ? "Online failed: the host didn't answer in time"
                    : e.getMessage() != null ? e.getMessage() : "Online failed");
        }
        engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.ONLINE_COMPLETED)
                .put(TransactionStepEvent.KEY_RESULT, result.getMessage())
                .build());
        return result;
    }

    /**
     * The longest one online authorization may take, end to end — token refresh, exchange and
     * sale/refund together. Each HTTP call alone may take 75 s to connect and 75 s to read, which
     * chained came to minutes with the terminal busy throughout. Past this the request is
     * cancelled and the transaction is online failed, like any host that doesn't answer.
     */
    protected long onlineTimeoutMs() {
        return 90_000;
    }

    /**
     * Print receipt lines via the terminal-owned {@link PrinterBehavior}, on
     * {@link #printScheduler} and returns at once — a real printer is slow enough that printing on
     * the calling (UI) thread would ANR. A failure (no paper, DAL not ready, printer busy) is
     * logged, not thrown — the caller asked to print and walked away; it has no exception handler
     * waiting.
     */
    public void printReceipt(List<String> lines) {
        printer.print(lines)
                .subscribeOn(printScheduler)
                .subscribe(() -> { }, Throwable::printStackTrace);
    }

    // ─── Public API (vendor-agnostic) ────────────────────────────────────

    /**
     * Every transaction's steps, replaying the latest one to a new subscriber — for engine-side
     * observers and tests. A screen observes the stream {@link #startTransaction} returned
     * instead: that one carries its own transaction's steps only.
     */
    public Observable<TransactionStepEvent> transactionSteps() {
        return engine.transactionSteps();
    }

    public Observable<EmvStepEvent> emvSteps() {
        return engine.emvSteps();
    }

    /**
     * The current transaction's APDU trace — owned by the engine, alongside
     * {@link #transactionSteps()} and {@link #emvSteps()}: observe
     * {@link ApduTrace#changes()} and read {@link ApduTrace#text()}.
     */
    public ApduTrace apduTrace() {
        return engine.apduTrace();
    }

    public TransactionStepEvent currentState() {
        return engine.currentTransactionState();
    }

    /**
     * Accept any presented card (chip / tap / swipe / manual as enabled by the vendor).
     * Preferred UI entry point — see {@link #startTransaction} for the stream it returns.
     */
    public final Observable<TransactionStepEvent> acceptCard(TransactionType type, long amountMinor) {
        return acceptCard(type, amountMinor, null);
    }

    /**
     * {@link #acceptCard(TransactionType, long)} with the original sale's reference number, which
     * a refund needs for the host — see {@link TransactionConfig#getReferenceNumber()}.
     */
    public final Observable<TransactionStepEvent> acceptCard(TransactionType type, long amountMinor,
            @Nullable String referenceNumber) {
        return startTransaction(new TransactionConfig(type, amountMinor, EntryMethod.ANY)
                .withReferenceNumber(referenceNumber));
    }

    /**
     * Start with an explicit reader mode. Nothing happens until the returned stream is subscribed;
     * each subscribe is one transaction attempt. The subscribe returns at once: card search, EMV
     * and the host run on the transaction thread, and every outcome (card found, search
     * cancelled, timed out, or errored) arrives as a {@link CardSearchListener} callback there.
     *
     * <ul>
     *   <li>{@code onNext}: this transaction's steps, from {@code TRANSACTION_STARTED} to
     *       {@code COMPLETED} or {@code ERROR} — never the previous transaction's.</li>
     *   <li>{@code onComplete}: the transaction is done with the reader; the terminal is free.</li>
     *   <li>{@code onError(}{@link TerminalBusyException}{@code )}: refused on the subscribing
     *       thread, nothing queued, while the previous transaction hasn't finished — still
     *       searching or running EMV, or cancelled but not yet done with the reader (a PAX poll
     *       takes a moment to stop). It is not reported on the previous transaction's steps.</li>
     *   <li>{@code dispose()}: cancels this transaction — its search stops and its EMV winds
     *       down, or it never runs if it hadn't begun yet. It never interrupts the transaction
     *       thread, and the terminal stays busy until that is done with the reader. This
     *       stream's steps stop at once, the cancel's own ERROR included.</li>
     * </ul>
     */
    public Observable<TransactionStepEvent> startTransaction(TransactionConfig config) {
        return Observable.create(source -> {
            // Steps come from the transaction thread, a cancel's ERROR from the UI thread.
            ObservableEmitter<TransactionStepEvent> emitter = source.serialize();
            Transaction transaction = new Transaction(config, emitter);
            // Here, on the subscribing thread: a refused start is reported at once and never
            // waits on the transaction thread to run later.
            if (!current.compareAndSet(null, transaction)) {
                EmvLog.d("POS terminal is busy: the previous transaction hasn't finished");
                emitter.tryOnError(new TerminalBusyException());
                return;
            }
            // skip(1): the step the engine replays belongs to the previous transaction — this one
            // hasn't run yet, so its first step is still to come.
            transaction.steps = engine.transactionSteps().skip(1).subscribe(emitter::onNext);
            emitter.setCancellable(transaction::cancel);
            // Not tied to the subscription: run() always runs — even disposed before it began, it
            // returns at once — so it always frees the terminal.
            transactionScheduler.scheduleDirect(transaction::run);
        });
    }

    /** Cancels the transaction still on this terminal, if any; the terminal is going away. */
    public void dispose() {
        Transaction transaction = current.get();
        if (transaction != null) {
            transaction.cancel();
        }
        engine.dispose();
    }

    /** Whether the search calling this should stop: its transaction was cancelled. */
    public final boolean isSearchCancelled() {
        Transaction transaction = current.get();
        return transaction != null && transaction.isOver();
    }

    /**
     * Debug switch for the {@link #apduTrace()}: whether to record every APDU command/response
     * and step. Always off in a release build (the trace holds card data).
     */
    public final void setApduLoggingEnabled(boolean enabled) {
        engine.apduTrace().setEnabled(enabled);
    }

    public final boolean isApduLoggingEnabled() {
        return engine.apduTrace().isEnabled();
    }

    // ─── Vendor must implement ───────────────────────────────────────────

    /**
     * Blocking card search using the vendor's native SDK.
     * Fire {@link CardSearchListener} events; return the selected card or {@code null}.
     */
    @Nullable
    public abstract CardPresence searchCard(TransactionConfig config, CardSearchListener listener);

    protected abstract void cancelCardSearch();

    protected void initializeVendor() {
        // default: no-op
    }

    // ─── Orchestration (fixed for all vendors) ───────────────────────────

    /**
     * One accepted {@link #startTransaction}: its config, its subscriber, and whether it was
     * cancelled. The terminal outlives every transaction, so what belongs to one transaction lives
     * here — a search still running for an earlier transaction can then tell it is over, instead
     * of acting on the next.
     */
    private final class Transaction {

        final TransactionConfig config;
        final ObservableEmitter<TransactionStepEvent> emitter;
        final AtomicBoolean cancelled = new AtomicBoolean();
        /** The engine's steps forwarded to {@link #emitter}; set before {@link #run} is scheduled. */
        Disposable steps = Disposable.disposed();

        Transaction(TransactionConfig config, ObservableEmitter<TransactionStepEvent> emitter) {
            this.config = config;
            this.emitter = emitter;
        }

        /**
         * Its search stops and its EMV winds down. Only while it is still the terminal's
         * transaction: once done — the emitter's own dispose after {@code onComplete} lands here
         * too — the reader and the engine may belong to the next one.
         */
        void cancel() {
            if (current.get() != this) {
                return;
            }
            cancelled.set(true);
            cancelCardSearch();
            engine.cancel();
        }

        /** Cancelled, or no longer the terminal's transaction: nothing it reports reaches the engine. */
        boolean isOver() {
            return cancelled.get() || current.get() != this;
        }

        /**
         * Runs on {@link #transactionScheduler}. One {@link EmvEngine#begin()} guards the whole
         * transaction, including any retries a behavior requests via {@link EmvEngine#requestRetry}
         * (PAX fallback / try-another-interface / try-again). Never throws: nothing above it would
         * catch it. Frees the terminal when it returns — done with the reader — and only then
         * completes its stream, so {@code onComplete} means "free".
         */
        void run() {
            try {
                if (isOver() || !engine.begin()) {
                    return;
                }
                triggerSearch(this, config);
            } catch (Throwable t) {
                //noinspection CallToPrintStackTrace
                t.printStackTrace();
                if (!isOver()) {
                    engine.notifyError(t.getMessage() != null ? t.getMessage() : "Transaction failed");
                }
            } finally {
                // Stop forwarding first: once the terminal is free, the next transaction's steps
                // go through the same engine.
                steps.dispose();
                current.compareAndSet(this, null);
                emitter.onComplete();
            }
        }
    }

    /**
     * Fires card search and returns immediately — search and EMV are not sequenced by a
     * blocking return value here. Every {@link CardSearchListener} implementation (Pax/
     * Ingenico/Fake) always calls exactly one listener method before it stops searching, so
     * that callback — not this method's return — is what drives what happens next:
     * {@link EngineReportingListener#handleCardFound} starts EMV the moment a card is found,
     * and a behavior-requested retry re-enters this same method with the adjusted config —
     * still on this one thread, so it never races the attempt that requested it.
     */
    private void triggerSearch(Transaction transaction, TransactionConfig config) {
        if (!engine.prepare(config)) {
            if (!transaction.isOver()) {
                engine.notifyError("Terminal initialization failed");
            }
            return;
        }
        searchCard(config, new EngineReportingListener(transaction, config));
    }

    /**
     * Maps reader events → engine subjects for the UI, and starts EMV once a card is found. Belongs
     * to one {@link Transaction}: once that is over, its callbacks are dropped, so a search that
     * outlived it can't fail, or start EMV for, the next one.
     */
    private final class EngineReportingListener implements CardSearchListener {

        private final Transaction transaction;
        private final TransactionConfig config;

        EngineReportingListener(Transaction transaction, TransactionConfig config) {
            this.transaction = transaction;
            this.config = config;
        }

        private boolean isStale(String callback) {
            if (transaction.isOver()) {
                EmvLog.d("Ignored " + callback + " from a search whose transaction is over");
                return true;
            }
            return false;
        }

        @Override
        public void onSearchStarted(TransactionConfig config) {
            if (isStale("onSearchStarted")) {
                return;
            }
            String prompt = engine.consumeRetryPrompt();
            String msg = prompt != null ? prompt : waitingMessage(config);
            TransactionStepEvent.Builder waiting = TransactionStepEvent
                    .builder(TransactionStep.WAITING_FOR_CARD).message(msg);
            if (prompt != null) {
                waiting.put(TransactionStepEvent.KEY_RETRY_PROMPT, prompt);
            }
            engine.notifyTransactionStep(waiting.build());
            engine.notifyEmvStep(EmvStep.SEARCH_CARD, msg);
        }

        @Override
        public void onChipDetected(CardPresence card) {
            handleCardFound(card);
        }

        @Override
        public void onContactlessDetected(CardPresence card) {
            handleCardFound(card);
        }

        @Override
        public void onMagstripeDetected(CardPresence card) {
            handleCardFound(card);
        }

        @Override
        public void onManualEntrySelected(CardPresence card) {
            handleCardFound(card);
        }

        @Override
        public void onCardRemoved() {
            if (isStale("onCardRemoved")) {
                return;
            }
            engine.notifyTransactionStep(TransactionStepEvent.of(
                    TransactionStep.WAITING_FOR_CARD, "Card removed"));
        }

        @Override
        public void onSearchTimeout() {
            if (isStale("onSearchTimeout")) {
                return;
            }
            engine.notifyError("No card presented — card search timed out");
        }

        @Override
        public void onSearchCancelled() {
            if (isStale("onSearchCancelled")) {
                return;
            }
            if (engine.isRunning()) {
                engine.notifyError("Card search cancelled");
            }
        }

        @Override
        public void onReaderError(String message) {
            if (isStale("onReaderError")) {
                return;
            }
            engine.notifyError(message != null ? message : "Reader error");
        }

        @Override
        public void onSearchRetry(String message) {
            if (isStale("onSearchRetry")) {
                return;
            }
            engine.notifyTransactionStep(TransactionStepEvent.builder(
                    TransactionStep.WAITING_FOR_CARD).message(message)
                    .put(TransactionStepEvent.KEY_RETRY_PROMPT, message).build());
            engine.notifyEmvStep(EmvStep.SEARCH_CARD, message);
        }

        /** Runs EMV right here, then re-triggers search if the behavior asked for a retry. */
        private void handleCardFound(CardPresence card) {
            if (isStale("card detected")) {
                return;
            }
            engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                    .put(TransactionStepEvent.KEY_MODE, card.getModeLabel())
                    .put("entryMethod", card.getEntryMethod().name())
                    .build());

            engine.start(config, card);

            // Consumed either way: a retry requested by a cancelled transaction never searches.
            TransactionConfig retryConfig = engine.consumePendingRetry();
            if (retryConfig != null && !transaction.isOver()) {
                triggerSearch(transaction, retryConfig);
            }
        }
    }

    private static String waitingMessage(TransactionConfig config) {
        if (config.getMode() == EntryMethod.ANY) {
            return "Insert, tap, or swipe";
        }
        if (config.isContact()) {
            return "Insert card";
        }
        if (config.isMagstripe()) {
            return "Swipe card";
        }
        if (config.isManual()) {
            return "Enter card number";
        }
        return "Tap card";
    }
}
