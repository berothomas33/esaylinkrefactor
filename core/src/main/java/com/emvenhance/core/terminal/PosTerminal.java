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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.observers.SafeObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The only public hardware API for the app.
 *
 * <p>Owns card search, host communication, and printer. EMV after entry selection is the
 * vendor's {@link AbstractEmvBehavior}, run by this terminal for each attempt with the attempt's
 * {@link Transaction}; the behavior keeps the {@link EmvEngine} (EMV steps, APDU trace, retry,
 * the way online), which this terminal reaches through {@link AbstractEmvBehavior#engine()}.
 *
 * <pre>
 *   UI → PosTerminal.startTransaction(ANY|CHIP|…)   [returns its Transaction at once]
 *          └── transaction thread: transaction.run()
 *          └── behavior.prepare(config, transaction) + searchCard(config, listener) ← vendor SDK
 *                 └── listener.onXxxDetected(card)                 ← vendor callback
 *                        └── behavior.start(config, card, transaction) ← vendor EMV, from inside the callback
 *                               └── behavior → engine.authorize → PosTerminal.authorize → host
 * </pre>
 *
 * <p>The terminal owns the whole life of a transaction in one place: busy ({@link #current})
 * and the {@link Transaction} itself — its state, its steps, its cancel and its end. The end —
 * COMPLETED, ERROR or CANCELLED, exactly once — is published only after the terminal is free
 * again, so a screen that sees it can start the next sale at once.
 */
public abstract class PosTerminal {

    /**
     * The vendor's EMV: this terminal runs it for each attempt, handing it the transaction, and
     * reaches the EMV engine it keeps through {@link AbstractEmvBehavior#engine()}.
     */
    protected final AbstractEmvBehavior behavior;

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
     * The one thread a transaction's acknowledgement or reversal goes to the host on, after its
     * end is published: the screen doesn't wait for it, and the next sale's online request
     * doesn't wait behind it on the transaction thread.
     */
    private final Scheduler followUpScheduler =
            Schedulers.from(Executors.newSingleThreadExecutor());

    /**
     * The transaction on the transaction thread, from its accepted start until it is done with
     * the reader; empty when the terminal is free (busy = non-empty). Only it may report steps.
     */
    private final AtomicReference<Transaction> current = new AtomicReference<>();

    /** @param behavior the vendor's EMV: given its engine once, run for each attempt */
    protected PosTerminal(AbstractEmvBehavior behavior,
            CommunicationBehavior communication,
            PrinterBehavior printer) {
        this.behavior = behavior;
        this.communication = communication;
        this.printer = printer;
        behavior.attach(new EmvEngine(this::authorize));
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
     *
     * <p>Names the request here, before it is sent ({@link TransactionConfig#getAsyncRequestId()}),
     * and records it with its answer on the transaction going online: a request that never got
     * an answer must still be reversed by that name once the transaction ends
     * ({@link Transaction#closeOnline}).
     */
    public AuthResult authorize(TransactionConfig config) {
        publish(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        publish(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));
        TransactionConfig sent = config.withAsyncRequestId(UUID.randomUUID().toString());
        Transaction transaction = current.get();
        OnlineRequest request = transaction != null ? transaction.wentOnline(sent) : null;
        AuthResult result;
        try {
            // Cancel doesn't cut this short: once sent, the sale may be approved at the host, and
            // the answer decides whether it is acknowledged or reversed — so the terminal waits
            // for it, or the timeout.
            result = communication.authorize(sent)
                    .timeout(onlineTimeoutMs(), TimeUnit.MILLISECONDS)
                    .blockingGet();
        } catch (Exception e) {
            // No usable host answer (network, timeout, envelope error): online failed, not a
            // host decline.
            result = AuthResult.failed(e.getCause() instanceof TimeoutException
                    ? "Online failed: the host didn't answer in time"
                    : e.getMessage() != null ? e.getMessage() : "Online failed");
        }
        if (request != null) {
            request.answer = result;
        }
        publish(TransactionStepEvent.builder(TransactionStep.ONLINE_COMPLETED)
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
        return behavior.engine().emvSteps();
    }

    /**
     * The current transaction's APDU trace — owned by the engine, alongside
     * {@link #emvSteps()}: observe
     * {@link ApduTrace#changes()} and read {@link ApduTrace#text()}.
     */
    public ApduTrace apduTrace() {
        return behavior.engine().apduTrace();
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
        transactionExecutor.execute(transaction::run);
        return transaction;
    }

    /** Cancels the transaction still on this terminal, if any; the terminal is going away. */
    public void dispose() {
        Transaction transaction = current.get();
        if (transaction != null) {
            transaction.cancel();
        }
        behavior.dispose();
    }

    /**
     * Whether a transaction is on this terminal (from its accepted start until it is done with
     * the reader) — what refuses a second start. Nothing else may rewrite what it reads (EMV
     * parameters) meanwhile.
     */
    public final boolean isBusy() {
        return current.get() != null;
    }

    /** Whether the vendor search polling for the current transaction should stop: it is over. */
    public final boolean isSearchCancelled() {
        Transaction transaction = current.get();
        return transaction != null && !transaction.isRunning();
    }

    /** {@link #authorize}'s steps, on the transaction going online: the current one. */
    private void publish(TransactionStepEvent event) {
        Transaction transaction = current.get();
        if (transaction == null) {
            EmvLog.d("TransactionStep dropped, no transaction: " + event);
            return;
        }
        transaction.publish(event);
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
        apduTrace().transactionEvent(sb.toString());
    }

    /**
     * Debug switch for the {@link #apduTrace()}: whether to record every APDU command/response
     * and step. Always off in a release build (the trace holds card data).
     */
    public final void setApduLoggingEnabled(boolean enabled) {
        apduTrace().setEnabled(enabled);
    }

    public final boolean isApduLoggingEnabled() {
        return apduTrace().isEnabled();
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
     * One accepted {@link #startTransaction}: everything about it, in one place — it runs itself
     * on the transaction thread ({@link #run}). The terminal outlives every transaction, so what
     * belongs to one lives here — a search still running for an earlier transaction can then
     * tell it is over, instead of acting on the next.
     *
     * <ul>
     *   <li>{@link #end} is its whole state: {@code null} while it runs (and the terminal is busy
     *       with it); the first COMPLETED / ERROR, or the cancel's CANCELLED, once it has ended.
     *       Written only under the lock below, so a cancel racing the end, or two ends from two
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
        @Nullable
        private volatile TransactionStepEvent end;
        private final ReplaySubject<TransactionStepEvent> steps = ReplaySubject.create();
        /** What it sent the host, in order; written on the transaction thread only. */
        private final List<OnlineRequest> online = new ArrayList<>();
        /** Whether it reported APPROVED; written under the lock. */
        private boolean approvedReported;

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
                if (end != null) {
                    return;
                }
                end = TransactionStepEvent.of(TransactionStep.CANCELLED);
                // Under the lock: before the end, whichever thread is first.
                deliver(TransactionStepEvent.of(TransactionStep.CANCELLING,
                        "Cancelling — finishing with the card"));
            }
            cancelCardSearch();
        }

        /** False once it has ended — an outcome or a cancel; the terminal is freed only after. */
        boolean isRunning() {
            return end == null;
        }

        boolean isCancelled() {
            TransactionStepEvent e = end;
            return e != null && e.getStep() == TransactionStep.CANCELLED;
        }

        /**
         * Every step of this transaction comes through here — the terminal's own and its
         * behavior's ({@link AbstractEmvBehavior#publish}). Published only while it is running;
         * the first end ends it and is published by {@link #run} once the terminal is free;
         * anything after it is dropped.
         */
        void publish(TransactionStepEvent event) {
            synchronized (steps) {
                if (end != null) {
                    EmvLog.d("TransactionStep dropped, the transaction has ended: " + event);
                    apduTrace().note("EMV",
                            "transaction step dropped, the transaction has ended: " + event);
                    return;
                }
                if (event.getStep().isEnd()) {
                    end = event;
                    return;
                }
                if (event.getStep() == TransactionStep.APPROVED) {
                    approvedReported = true;
                }
                deliver(event);
            }
        }

        /**
         * Delivers {@code event} to every subscriber; its caller holds the lock. A subscriber
         * that throws doesn't reach here: its {@link SafeObserver} ({@link #steps()}) takes it.
         */
        private void deliver(TransactionStepEvent event) {
            EmvLog.d("TransactionStep: " + event);
            traceTransactionStep(event);
            steps.onNext(event);
        }

        /**
         * Runs on the transaction thread — always, even for a transaction cancelled before it got
         * here, so it always frees the terminal. One {@link EmvEngine#begin()} for the whole
         * transaction, including any retries a behavior requests via {@link EmvEngine#requestRetry}
         * (PAX fallback / try-another-interface / try-again). Never throws.
         *
         * <p>Its {@code finally} is the one place a transaction's end is published: frees the
         * terminal first, then publishes the end, so a screen that sees COMPLETED/ERROR can start
         * the next sale at once.
         */
        private void run() {
            try {
                behavior.engine().begin();
                publish(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED)); // once
                // Each attempt: prepare, search, EMV. A retry the behavior asked for (fallback,
                // try another interface, try again, See Phone) is the next turn of this loop —
                // not a search started from inside the previous search's callback.
                TransactionConfig attemptConfig = config;
                while (attemptConfig != null && isRunning()) {
                    attemptConfig = attempt(attemptConfig);
                }
            } catch (Throwable t) {
                //noinspection CallToPrintStackTrace
                t.printStackTrace();
                publish(errorEvent(t.getMessage() != null ? t.getMessage() : "Transaction failed"));
            } finally {
                List<Runnable> followUps;
                synchronized (steps) { // a cancel's CANCELLING can't land after the end
                    // A search that stopped with no outcome still ends its transaction.
                    if (end == null) {
                        end = errorEvent("Transaction ended without an outcome");
                    }
                    followUps = closeOnline();
                    current.compareAndSet(this, null);
                    deliver(end);
                }
                for (Runnable followUp : followUps) {
                    followUp.run();
                }
            }
        }

        /** The request it is about to send the host: recorded before it goes. */
        OnlineRequest wentOnline(TransactionConfig sent) {
            OnlineRequest request = new OnlineRequest(sent);
            online.add(request);
            return request;
        }

        /**
         * Closes the host's side of every request this transaction sent, now that it has ended;
         * called under the lock, with {@link #end} set. Notes each in the APDU trace and returns
         * the calls, which {@link #run} makes once the terminal is free.
         *
         * <ul>
         *   <li>Its last request, approved by the host, and the transaction reported APPROVED and
         *       ended COMPLETED: <b>acknowledge</b> — the sale is done.</li>
         *   <li>Approved by the host, but the transaction didn't end that way (the card refused it
         *       at the 2nd GENERATE AC, a cancel, an error) — or an earlier request superseded by
         *       a later one: <b>reverse</b>.</li>
         *   <li>Sent with no usable answer (failed, timed out): <b>reverse</b> — the host may
         *       have approved it. Every failed answer counts, even one the host side failed
         *       before sending (a reversal of a request the host never got does no harm; a
         *       missing one charges the cardholder).</li>
         *   <li>Declined by the host: nothing to close.</li>
         * </ul>
         */
        private List<Runnable> closeOnline() {
            List<Runnable> followUps = new ArrayList<>();
            boolean completedApproved = approvedReported
                    && end.getStep() == TransactionStep.COMPLETED;
            for (int i = 0; i < online.size(); i++) {
                OnlineRequest request = online.get(i);
                AuthResult answer = request.answer;
                boolean last = i == online.size() - 1;
                String id = request.sent.getAsyncRequestId();
                if (answer != null && answer.isApproved() && last && completedApproved) {
                    apduTrace().note("HOST", "ACKNOWLEDGEMENT for request " + id);
                    followUps.add(() -> sendFollowUp("Acknowledgement " + id,
                            Completable.defer(() -> communication.acknowledge(request.sent))));
                } else if (answer == null || answer.isApproved() || answer.isFailed()) {
                    apduTrace().note("HOST", "REVERSAL for request " + id + ": "
                            + (answer == null || answer.isFailed()
                                    ? "no host answer" : "approved, not completed"));
                    followUps.add(() -> sendFollowUp("Reversal " + id,
                            Completable.defer(() -> communication.reverse(request.sent))));
                }
            }
            return followUps;
        }

        /**
         * One attempt: terminal initialization, then card search. Every {@link CardSearchListener}
         * implementation (Pax/Ingenico/Fake) calls exactly one listener method before it stops
         * searching, and that callback drives what happens next:
         * {@link EngineReportingListener#handleCardFound} runs EMV the moment a card is found.
         *
         * @return the adjusted config of a retry the behavior asked for
         *         ({@link EmvEngine#requestRetry}), or {@code null}: the transaction is done
         */
        @Nullable
        private TransactionConfig attempt(TransactionConfig config) {
            if (!behavior.prepare(config, this)) {
                // Dropped if it already ended.
                publish(errorEvent("Terminal initialization failed"));
                return null;
            }
            searchCard(config, new EngineReportingListener(config));
            // Consumed either way; run's loop drops it if the transaction is over (cancelled).
            TransactionConfig retry = behavior.engine().consumePendingRetry();
            if (retry != null && !isRunning()) {
                EmvLog.d("Retry dropped: the transaction is over");
                apduTrace().note("EMV", "retry dropped — the transaction is over");
            }
            return retry;
        }

        /**
         * Maps reader events → this transaction's steps, and starts EMV once a card is found.
         * Once the transaction is over, its callbacks are dropped, so a search that outlived it
         * can't fail, or start EMV for, the next one.
         */
        private final class EngineReportingListener implements CardSearchListener {

            private final TransactionConfig config;

            EngineReportingListener(TransactionConfig config) {
                this.config = config;
            }

            private boolean isStale(String callback) {
                if (!isRunning()) {
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
                String prompt = behavior.engine().consumeRetryPrompt();
                String msg = prompt != null ? prompt : waitingMessage(config);
                TransactionStepEvent.Builder waiting = TransactionStepEvent
                        .builder(TransactionStep.WAITING_FOR_CARD).message(msg);
                if (prompt != null) {
                    waiting.put(TransactionStepEvent.KEY_RETRY_PROMPT, prompt);
                }
                publish(waiting.build());
                behavior.engine().notifyEmvStep(EmvStep.SEARCH_CARD, msg);
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
                publish(TransactionStepEvent.of(TransactionStep.WAITING_FOR_CARD, "Card removed"));
            }

            @Override
            public void onSearchTimeout() {
                if (isStale("onSearchTimeout")) {
                    return;
                }
                publish(errorEvent("No card presented — card search timed out"));
            }

            @Override
            public void onSearchCancelled() {
                if (isStale("onSearchCancelled")) {
                    return;
                }
                publish(errorEvent("Card search cancelled"));
            }

            @Override
            public void onReaderError(String message) {
                if (isStale("onReaderError")) {
                    return;
                }
                publish(errorEvent(message != null ? message : "Reader error"));
            }

            @Override
            public void onSearchRetry(String message) {
                if (isStale("onSearchRetry")) {
                    return;
                }
                publish(TransactionStepEvent.builder(
                        TransactionStep.WAITING_FOR_CARD).message(message)
                        .put(TransactionStepEvent.KEY_RETRY_PROMPT, message).build());
                behavior.engine().notifyEmvStep(EmvStep.SEARCH_CARD, message);
            }

            /** Runs EMV right here; a retry it asks for is {@link #attempt}'s to return. */
            private void handleCardFound(CardPresence card) {
                if (isStale("card detected")) {
                    return;
                }
                publish(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                        .put(TransactionStepEvent.KEY_MODE, card.getModeLabel())
                        .put("entryMethod", card.getEntryMethod().name())
                        .build());

                behavior.start(config, card, Transaction.this);
            }
        }
    }

    /** One request a transaction sent the host, and the host's answer to it once it came. */
    private static final class OnlineRequest {

        final TransactionConfig sent;
        /** {@code null} until {@link #authorize} has it — a request still in flight. */
        @Nullable
        volatile AuthResult answer;

        OnlineRequest(TransactionConfig sent) {
            this.sent = sent;
        }
    }

    /**
     * Sends a transaction's acknowledgement or reversal on {@link #followUpScheduler}, at most
     * {@link #onlineTimeoutMs()}, and returns at once. Nobody waits for it: a failure is logged,
     * and not retried — the host reverses on its own a sale it never got acknowledged.
     */
    private void sendFollowUp(String what, Completable call) {
        call.timeout(onlineTimeoutMs(), TimeUnit.MILLISECONDS)
                .subscribeOn(followUpScheduler)
                .subscribe(() -> EmvLog.d(what + " sent"),
                        e -> EmvLog.d(what + " failed: " + e));
    }

    /** An ERROR step: the terminal's own, and its behavior's ({@link AbstractEmvBehavior#finishError}). */
    static TransactionStepEvent errorEvent(String error) {
        return TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error)
                .put(TransactionStepEvent.KEY_ERROR, error)
                .build();
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
