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
import com.emvenhance.core.param.EmvParams;
import com.emvenhance.core.util.ApduTrace;
import com.emvenhance.core.util.EmvLog;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.observers.SafeObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The only public hardware API for the app.
 *
 * <p>Owns card search, host communication, and printer. EMV after entry selection is
 * delegated to its {@link EmvEngine}, which owns the vendor's {@link EmvBehavior}.
 *
 * <pre>
 *   UI → PosTerminal.startTransaction(ANY|CHIP|…)   [returns its Transaction at once]
 *          └── transaction thread: process(transaction)
 *          └── engine.prepare + searchCard(config, listener)        ← vendor SDK
 *                 └── listener.onXxxDetected(card)                 ← vendor callback
 *                        └── engine.start(card)                     ← vendor EMV, from inside the callback
 *                               └── behavior → engine.authorize → PosTerminal.authorize → host
 * </pre>
 *
 * <p>The terminal owns the whole life of a transaction in one place: busy ({@link #current})
 * and the {@link Transaction} itself — its state, its steps, its cancel and its end. The end —
 * COMPLETED, ERROR or CANCELLED, exactly once — is published only after the terminal is free
 * again, so a screen that sees it can start the next sale at once.
 */
public abstract class PosTerminal {

    protected final EmvEngine engine;
    protected final CommunicationBehavior communication;
    protected final PrinterBehavior printer;

    /**
     * The one thread every transaction runs on, from card search through EMV and the host: one
     * reader, one kernel. Nothing ever queues on it — {@link #startTransaction} refuses a start
     * while the previous transaction is still there — and nothing ever interrupts it: a cancel
     * is a flag the transaction reads, never an interrupt of a native call.
     */
    private final Executor transactionExecutor = Executors.newSingleThreadExecutor();

    /**
     * The one thread receipts print on: one printer, one job at a time, in the order they were
     * asked for — two quick taps on Print queue the second receipt instead of driving the printer
     * from two threads at once.
     */
    private final Scheduler printScheduler =
            Schedulers.from(Executors.newSingleThreadExecutor());

    /**
     * The transaction on the transaction thread, from its accepted start until it is done with
     * the reader; empty when the terminal is free (busy = non-empty). Only it may report steps.
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
        this.engine = new EmvEngine(behavior, this::authorize, this::publish,
                this::isTransactionRunning, this::isTransactionCancelled);
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

    public Observable<EmvStepEvent> emvSteps() {
        return engine.emvSteps();
    }

    /**
     * The current transaction's APDU trace — owned by the engine, alongside
     * {@link #emvSteps()}: observe
     * {@link ApduTrace#changes()} and read {@link ApduTrace#text()}.
     */
    public ApduTrace apduTrace() {
        return engine.apduTrace();
    }

    /**
     * Accept any presented card (chip / tap / swipe / manual as enabled by the vendor).
     * Preferred UI entry point — see {@link #startTransaction}.
     */
    public final Transaction acceptCard(TransactionType type, long amountMinor) {
        return acceptCard(type, amountMinor, null);
    }

    /**
     * {@link #acceptCard(TransactionType, long)} with the original sale's reference number, which
     * a refund needs for the host — see {@link TransactionConfig#getReferenceNumber()}.
     */
    public final Transaction acceptCard(TransactionType type, long amountMinor,
            @Nullable String referenceNumber) {
        return startTransaction(new TransactionConfig(type, amountMinor, EntryMethod.ANY)
                .withReferenceNumber(referenceNumber));
    }

    /**
     * Start with an explicit reader mode: card search, EMV and the host run on the transaction
     * thread and this returns at once. Every outcome (card found, search cancelled, timed out,
     * or errored) arrives as a {@link CardSearchListener} callback there, and the transaction's
     * steps on {@link Transaction#steps()}, ending with COMPLETED, ERROR or CANCELLED once the
     * terminal is free.
     *
     * @return this transaction: its {@link Transaction#steps() steps}, and the handle to
     *         {@link Transaction#cancel() cancel} it — that one only, never another's
     * @throws TerminalBusyException at once, on the calling thread, nothing queued: the previous
     *         transaction hasn't finished — still searching or running EMV, or cancelled but not
     *         yet done with the reader (a PAX poll takes a moment to stop). Nothing is reported
     *         on the steps of the transaction still there.
     */
    public Transaction startTransaction(TransactionConfig config) {
        Transaction transaction = new Transaction(config);
        if (!current.compareAndSet(null, transaction)) {
            EmvLog.d("POS terminal is busy: the previous transaction hasn't finished");
            throw new TerminalBusyException();
        }
        transactionExecutor.execute(() -> process(transaction));
        return transaction;
    }

    /** Cancels the transaction still on this terminal, if any; the terminal is going away. */
    public void dispose() {
        Transaction transaction = current.get();
        if (transaction != null) {
            transaction.cancel();
        }
        engine.dispose();
    }

    /** Whether the vendor search polling for the current transaction should stop: it is over. */
    public final boolean isSearchCancelled() {
        Transaction transaction = current.get();
        return transaction != null && transaction.isOver();
    }

    /** The engine's {@link EmvEngine#isRunning()}: the current transaction hasn't ended. */
    private boolean isTransactionRunning() {
        Transaction transaction = current.get();
        return transaction != null && transaction.isRunning();
    }

    /** The engine's {@link EmvEngine#isCancelled()}: the current transaction was cancelled. */
    private boolean isTransactionCancelled() {
        Transaction transaction = current.get();
        return transaction != null && transaction.isCancelled();
    }

    /**
     * Every transaction step comes through here ({@link EmvEngine#notifyTransactionStep} hands
     * the behavior's ones over). Published only while the current transaction is running, on
     * that transaction's {@link Transaction#steps()}; the first end ends it
     * ({@link Transaction#end}) and is published by {@link #process} once the terminal is free;
     * anything after it is dropped.
     */
    private void publish(TransactionStepEvent event) {
        Transaction transaction = current.get();
        if (transaction == null) {
            EmvLog.d("TransactionStep dropped, no transaction: " + event);
            return;
        }
        synchronized (transaction.steps) {
            if (!transaction.isRunning()) {
                EmvLog.d("TransactionStep dropped, the transaction has ended: " + event);
                engine.apduTrace().note("EMV",
                        "transaction step dropped, the transaction has ended: " + event);
                return;
            }
            if (event.getStep().isEnd()) {
                transaction.end(event);
                return;
            }
            transaction.publishNow(event);
        }
    }

    /** Flags each transaction step in the APDU trace. */
    private void traceTransactionStep(TransactionStepEvent event) {
        TransactionStep step = event.getStep();
        StringBuilder sb = new StringBuilder(step.getLabel().toUpperCase(Locale.US));
        Object mode = event.get(TransactionStepEvent.KEY_MODE);
        if (mode != null) {
            sb.append(" (").append(mode).append(')');
        }
        Object error = event.get(TransactionStepEvent.KEY_ERROR);
        Object result = event.get(TransactionStepEvent.KEY_RESULT);
        String message = event.getMessage();
        if (message != null && !message.equalsIgnoreCase(step.getLabel())) {
            sb.append(": ").append(message);
        } else if (error != null) {
            sb.append(": ").append(error);
        } else if (result != null) {
            sb.append(": ").append(result);
        }
        engine.apduTrace().transactionEvent(sb.toString());
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

    // ─── Terminal setup (a vendor overrides what it has) ─────────────────

    /** This terminal's serial number, which the host identifies it by; empty if the vendor has none. */
    public String serialNumber() {
        return "";
    }

    /** Runtime permissions the vendor's hardware needs (a screen asks for them); none by default. */
    public String[] requiredPermissions() {
        return new String[0];
    }

    /** The countries the terminal can be set to (its currency); empty: the vendor has no setting. */
    public List<String> countries() {
        return Collections.emptyList();
    }

    /**
     * Makes {@code country}, one of {@link #countries()}, the terminal's country.
     *
     * @return its currency code, e.g. "EGP"
     */
    public String setCountry(String country) {
        throw new UnsupportedOperationException("This terminal has no country setting");
    }

    /** The vendor's EMV parameters, for the EMV params screen; {@code null}: none to show. */
    @Nullable
    public EmvParams emvParams() {
        return null;
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
     * One accepted {@link #startTransaction}: everything about it, in one place. The terminal
     * outlives every transaction, so what belongs to one lives here — a search still running for
     * an earlier transaction can then tell it is over, instead of acting on the next.
     *
     * <ul>
     *   <li>{@link #end} is its whole state: {@code null} while it runs (and the terminal is busy
     *       with it); the first COMPLETED / ERROR, or the cancel's CANCELLED, once it has ended.
     *       One compare-and-set decides it, so a cancel racing the end, or two ends from two
     *       threads, end it exactly once.</li>
     *   <li>{@link #steps} is what it reports, for a screen: its own stream, so a new transaction
     *       never replays the last one's outcome. It replays all its steps to a screen that
     *       subscribes late or again (a rotation): APPROVED's data is still there after
     *       COMPLETED, and nothing is missed between the start and the subscribe. It never
     *       completes — the end is a step on it. It is
     *       also the lock of its reporting: every publish holds it, so a cancel's CANCELLING
     *       never lands after the end, and subscribers never get two {@code onNext} at once.</li>
     * </ul>
     */
    public final class Transaction {

        private final TransactionConfig config;
        private final AtomicReference<TransactionStepEvent> end = new AtomicReference<>();
        private final ReplaySubject<TransactionStepEvent> steps = ReplaySubject.create();

        private Transaction(TransactionConfig config) {
            this.config = config;
        }

        /**
         * This transaction's steps, from its first, ending with COMPLETED, ERROR or CANCELLED
         * once the terminal is free. Each subscriber is a {@link SafeObserver}: one that throws is
         * cut off alone, and the others go on getting every step.
         */
        public Observable<TransactionStepEvent> steps() {
            return Observable.wrap(observer -> steps.subscribe(new SafeObserver<>(observer)));
        }

        /**
         * Cancels this transaction: CANCELLING at once, its search stops and its EMV winds down
         * (or it never starts if it hadn't yet). Never interrupts the transaction thread: the
         * terminal stays busy until that is done with the reader — a host call already sent is
         * waited for — then reports CANCELLED. Does nothing once it has ended — a finished sale,
         * or a second cancel.
         */
        public void cancel() {
            synchronized (steps) {
                if (!end.compareAndSet(null, TransactionStepEvent.of(TransactionStep.CANCELLED))) {
                    return;
                }
                // Under the lock: before the end, whichever thread is first.
                publishNow(TransactionStepEvent.of(TransactionStep.CANCELLING,
                        "Cancelling — finishing with the card"));
            }
            cancelCardSearch();
        }

        boolean isRunning() {
            return end.get() == null;
        }

        boolean isCancelled() {
            TransactionStepEvent e = end.get();
            return e != null && e.getStep() == TransactionStep.CANCELLED;
        }

        /** Ended, or no longer the terminal's transaction: nothing it reports reaches the UI. */
        boolean isOver() {
            return !isRunning() || current.get() != this;
        }

        /** The first end wins; a later one (a stage noticing the cancel, say) is dropped. */
        private void end(TransactionStepEvent event) {
            if (!end.compareAndSet(null, event)) {
                EmvLog.d("TransactionStep dropped, the transaction has ended: " + event);
            }
        }

        /**
         * Delivers {@code event} to every subscriber, under the lock. A subscriber that throws is
         * logged and the delivery carries on: neither the stream nor the transaction thread
         * (which may be mid-EMV) goes down with it.
         */
        private void publishNow(TransactionStepEvent event) {
            synchronized (steps) {
                EmvLog.d("TransactionStep: " + event);
                traceTransactionStep(event);
                try {
                    steps.onNext(event);
                } catch (Throwable t) {
                    EmvLog.d("A transaction-steps subscriber threw on " + event + ": " + t);
                    engine.apduTrace().note("EMV", "a screen failed on " + event.getStep()
                            + " — the transaction carries on");
                }
            }
        }
    }

    /**
     * Runs on the transaction thread — always, even for a transaction cancelled before it got
     * here, so it always frees the terminal. One {@link EmvEngine#begin()} for the whole
     * transaction, including any retries a behavior requests via {@link EmvEngine#requestRetry}
     * (PAX fallback / try-another-interface / try-again). Never throws.
     *
     * <p>Its {@code finally} is the one place a transaction's end is published: frees the
     * terminal first, then publishes the end, so a screen that sees COMPLETED/ERROR can start the
     * next sale at once.
     */
    private void process(Transaction transaction) {
        try {
            engine.begin();
            publish(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED)); // once
            // Each attempt: prepare, search, EMV. A retry the behavior asked for (fallback,
            // try another interface, try again, See Phone) is the next turn of this loop — not a
            // search started from inside the previous search's callback.
            TransactionConfig config = transaction.config;
            while (config != null && transaction.isRunning()) {
                config = attempt(transaction, config);
            }
        } catch (Throwable t) {
            //noinspection CallToPrintStackTrace
            t.printStackTrace();
            transaction.end(errorEvent(t.getMessage() != null ? t.getMessage() : "Transaction failed"));
        } finally {
            synchronized (transaction.steps) { // a cancel's CANCELLING can't land after the end
                // A search that stopped with no outcome still ends its transaction.
                transaction.end.compareAndSet(null,
                        errorEvent("Transaction ended without an outcome"));
                current.compareAndSet(transaction, null);
                transaction.publishNow(transaction.end.get());
            }
        }
    }

    private static TransactionStepEvent errorEvent(String error) {
        return TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error)
                .put(TransactionStepEvent.KEY_ERROR, error)
                .build();
    }

    /**
     * One attempt: terminal initialization, then card search. Every {@link CardSearchListener}
     * implementation (Pax/Ingenico/Fake) calls exactly one listener method before it stops
     * searching, and that callback drives what happens next:
     * {@link EngineReportingListener#handleCardFound} runs EMV the moment a card is found.
     *
     * @return the adjusted config of a retry the behavior asked for ({@link EmvEngine#requestRetry}),
     *         or {@code null}: the transaction is done
     */
    @Nullable
    private TransactionConfig attempt(Transaction transaction, TransactionConfig config) {
        if (!engine.prepare(config)) {
            engine.notifyError("Terminal initialization failed"); // dropped if it already ended
            return null;
        }
        searchCard(config, new EngineReportingListener(transaction, config));
        // Consumed either way; process's loop drops it if the transaction is over (cancelled).
        TransactionConfig retry = engine.consumePendingRetry();
        if (retry != null && !transaction.isRunning()) {
            EmvLog.d("Retry dropped: the transaction is over");
            engine.apduTrace().note("EMV", "retry dropped — the transaction is over");
        }
        return retry;
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
            engine.notifyError("Card search cancelled");
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

        /** Runs EMV right here; a retry it asks for is {@link #attempt}'s to return. */
        private void handleCardFound(CardPresence card) {
            if (isStale("card detected")) {
                return;
            }
            engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                    .put(TransactionStepEvent.KEY_MODE, card.getModeLabel())
                    .put("entryMethod", card.getEntryMethod().name())
                    .build());

            engine.start(config, card);
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
