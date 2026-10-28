package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.transaction.TransactionType;
import com.emvenhance.core.emv.EmvEngine;
import com.emvenhance.core.emv.EmvStepEvent;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.transaction.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.HostNotReachedException;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.param.EmvParams;
import com.emvenhance.core.emv.ApduTrace;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The only public hardware API for the app.
 *
 * <p>Owns the transaction, host communication, and printer. The EMV — from terminal
 * initialization and the card search through the outcome, retries included — is the vendor's
 * {@link AbstractEmvBehavior}, run by this terminal once per transaction with its
 * {@link Transaction}; the behavior keeps the {@link EmvEngine} (EMV steps, APDU trace, retry,
 * the way online), which this terminal reaches through {@link AbstractEmvBehavior#engine()}.
 *
 * <pre>
 *   UI → PosTerminal.startTransaction(ANY|CHIP|…)   [returns its Transaction at once]
 *          └── TRANSACTION_STARTED → transaction thread: onTransactionStarted()
 *                 └── behavior.run(config, transaction)        ← the vendor's EMV
 *                        TERMINAL_INITIALIZATION → SEARCH_CARD (vendor polling)
 *                        → card found → the EMV steps → finish* (the outcome)
 *                        → a retry the kernel asked for: SEARCH_CARD again
 *                        └── behavior → engine.authorize → PosTerminal.authorize → host
 *                 └── onEnd()                                   ← the end, the terminal free
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
     * The one thread every transaction runs on, from the card search through EMV and the host: one
     * reader, one kernel. TRANSACTION_STARTED's method puts the transaction here
     * ({@code onTransactionStarted}). Only one transaction is ever on it —
     * {@link #startTransaction} refuses a start while the previous one is still there — and
     * nothing ever interrupts it ({@code Schedulers.from} doesn't interrupt its tasks): a cancel
     * is a flag the transaction reads, never an interrupt of a native call.
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
            // No usable host answer: online failed, not a host decline. Reversed, unless the
            // host side says the host could not have approved it.
            HostNotReachedException notReached = e instanceof HostNotReachedException
                    ? (HostNotReachedException) e
                    : e.getCause() instanceof HostNotReachedException
                            ? (HostNotReachedException) e.getCause() : null;
            result = notReached != null
                    ? AuthResult.notSent(notReached.getMessage() != null
                            ? notReached.getMessage() : "Online failed: not sent to the host")
                    : AuthResult.failed(e.getCause() instanceof TimeoutException
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
     * Start with an explicit reader mode: the EMV (card search included) and the host run on the
     * transaction thread and this returns at once. The transaction's steps arrive on
     * {@link Transaction#steps()}, ending with COMPLETED, ERROR or CANCELLED once the terminal is
     * free.
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
        transaction.begin();
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

    // ─── Vendor may override ─────────────────────────────────────────────

    protected void initializeVendor() {
        // default: no-op
    }

    // ─── Orchestration (fixed for all vendors) ───────────────────────────

    /**
     * One accepted {@link #startTransaction}: everything about it, in one place — driven by its
     * own steps, whose methods run as they are published ({@link #dispatchTransactionStep}). The
     * terminal outlives every transaction, so what belongs to one lives here — a search still
     * running for an earlier transaction can then tell it is over, instead of acting on the next.
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
            TransactionStepEvent cancelling = TransactionStepEvent.of(TransactionStep.CANCELLING,
                    "Cancelling — finishing with the card");
            synchronized (steps) {
                if (end != null) {
                    return;
                }
                end = TransactionStepEvent.of(TransactionStep.CANCELLED);
                // Under the lock: before the end, whichever thread is first.
                deliver(cancelling);
            }
            dispatchTransactionStep(cancelling);
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
         * the first end ends it and is published by {@link #onEnd} once the terminal is free;
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
                deliver(event);
            }
            dispatchTransactionStep(event);
        }

        /**
         * The EMV is done with the card and reports how ({@code AbstractEmvBehavior#finish*}):
         * APPROVED, DECLINED or ERROR. The transaction, not the EMV, ends itself here. Today the
         * end follows the card's outcome — APPROVED or DECLINED then COMPLETED, ERROR as it is —
         * and never turns a decline or an error into an approval. This is where an outcome that
         * still needs the host (e.g. Mastercard single tap: the host asks for a PIN) will be
         * decided.
         *
         * <p>The outcome and the end are set under one hold of the lock, so a cancel can't land
         * between them (APPROVED then CANCELLED). Dropped, with a trace note, once the transaction
         * has ended (a cancel was first).
         */
        void emvFinished(TransactionStepEvent outcome) {
            TransactionStep step = outcome.getStep();
            synchronized (steps) {
                if (end != null) {
                    EmvLog.d("EMV outcome dropped, the transaction has ended: " + outcome);
                    apduTrace().note("EMV", "EMV outcome dropped, the transaction has ended ("
                            + end.getStep() + "): " + step);
                    return;
                }
                if (step == TransactionStep.APPROVED || step == TransactionStep.DECLINED) {
                    apduTrace().note("EMV", "EMV finished: " + step + " — the transaction completes");
                    deliver(outcome);
                    end = TransactionStepEvent.of(TransactionStep.COMPLETED);
                } else if (step == TransactionStep.ERROR) {
                    apduTrace().note("EMV", "EMV finished with an error — the transaction ends");
                    end = outcome;
                } else {
                    apduTrace().note("EMV", "EMV finished with no outcome (" + step
                            + ") — the transaction ends with an error");
                    end = errorEvent("EMV finished with no outcome: " + step);
                }
            }
        }

        /**
         * Delivers {@code event} to every subscriber; its caller holds the lock. A subscriber
         * that throws doesn't reach here: its {@link SafeObserver} ({@link #steps()}) takes it.
         */
        private void deliver(TransactionStepEvent event) {
            if (event.getStep() == TransactionStep.APPROVED) {
                approvedReported = true;
            }
            EmvLog.d("TransactionStep: " + event);
            traceTransactionStep(event);
            steps.onNext(event);
        }

        /**
         * Starts this transaction, on the caller's thread right after the busy check: publishes
         * TRANSACTION_STARTED, whose method puts it on the transaction thread. Once.
         */
        private void begin() {
            publish(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        }

        /**
         * Runs the method of a step that drives the transaction, the way {@code goToStep} runs
         * an EMV step's: synchronously, on the thread that published it, once it is delivered
         * (outside the lock). Every other step is a report for the screen and runs nothing.
         *
         * <pre>
         *   TRANSACTION_STARTED → onTransactionStarted: on the transaction thread, the EMV, then
         *                         the end ({@link #onEnd})
         *   CANCELLING          → onCancelling: the card search stops
         * </pre>
         */
        private void dispatchTransactionStep(TransactionStepEvent event) {
            switch (event.getStep()) {
                case TRANSACTION_STARTED:
                    onTransactionStarted(event);
                    break;
                case CANCELLING:
                    onCancelling();
                    break;
                default:
                    break;
            }
        }

        /**
         * The transaction goes to its thread: its EMV, then its end. Scheduled on
         * {@link #transactionScheduler}, which runs every task it is given — never disposed,
         * never interrupted — so even a transaction cancelled before its turn comes still ends
         * there and frees the terminal.
         */
        private void onTransactionStarted(TransactionStepEvent started) {
            transactionScheduler.scheduleDirect(() -> runTransaction(started));
        }

        /**
         * On the transaction thread. One {@link EmvEngine#begin()} for the whole transaction,
         * retries included; it starts a fresh APDU trace, so TRANSACTION_STARTED (published on
         * the caller's thread before it) is written into the new trace here. The EMV runs
         * whatever retries the kernel asks for ({@link EmvBehavior#run}); a cancel first just
         * makes it return sooner. Whatever goes wrong, the transaction still ends and frees the
         * terminal.
         */
        private void runTransaction(TransactionStepEvent started) {
            try {
                behavior.engine().begin();
                traceTransactionStep(started);
                if (isRunning()) {
                    behavior.run(config, this);
                }
            } catch (Throwable t) {
                //noinspection CallToPrintStackTrace
                t.printStackTrace();
                publish(errorEvent(t.getMessage() != null ? t.getMessage() : "Transaction failed"));
            } finally {
                onEnd();
            }
        }

        /** The transaction was cancelled: its card search stops; the EMV winds down at its next step. */
        private void onCancelling() {
            behavior.cancelCardSearch();
        }

        /**
         * The one place a transaction's end is published, on the transaction thread, once (the
         * last thing {@link #runTransaction} does): the
         * end (or ERROR "Transaction ended without an outcome" if none was set), the host
         * follow-up decided ({@link #closeOnline}), the terminal freed, then the end delivered —
         * a screen that sees COMPLETED / ERROR / CANCELLED can start the next sale at once — and
         * last the acknowledgement / reversal sent.
         */
        private void onEnd() {
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

        /** The request it is about to send the host: recorded before it goes. */
        OnlineRequest wentOnline(TransactionConfig sent) {
            OnlineRequest request = new OnlineRequest(sent);
            online.add(request);
            return request;
        }

        /**
         * Closes the host's side of every request this transaction sent, now that it has ended;
         * called under the lock, with {@link #end} set. Notes each in the APDU trace and returns
         * the calls, which {@link #onEnd} makes once the terminal is free.
         *
         * <ul>
         *   <li>Its last request, approved by the host, and the transaction reported APPROVED and
         *       ended COMPLETED: <b>acknowledge</b> — the sale is done.</li>
         *   <li>Approved by the host, but the transaction didn't end that way (the card refused it
         *       at the 2nd GENERATE AC, a cancel, an error) — or an earlier request superseded by
         *       a later one: <b>reverse</b>.</li>
         *   <li>Sent with no usable answer (failed, timed out): <b>reverse</b> — the host may
         *       have approved it. Every failed answer counts unless the host side is certain the
         *       host could not have approved it (a reversal of a request the host never got does
         *       no harm; a missing one charges the cardholder).</li>
         *   <li>{@link AuthResult#notSent Not sent} (no token, the exchange failed, no
         *       connection, HTTP 401/403) or declined by the host: nothing to close; a not-sent
         *       one is noted in the trace with why.</li>
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
                } else if (answer != null && answer.isNotSent()) {
                    apduTrace().note("HOST", "no reversal for request " + id
                            + ": the host could not have approved it — " + answer.getMessage());
                } else if (answer == null || answer.isApproved() || answer.isFailed()) {
                    apduTrace().note("HOST", "REVERSAL for request " + id + ": "
                            + (answer == null ? "no host answer"
                                    : answer.isFailed() ? "no usable host answer — "
                                            + answer.getMessage()
                                            : "approved, not completed"));
                    followUps.add(() -> sendFollowUp("Reversal " + id,
                            Completable.defer(() -> communication.reverse(request.sent))));
                }
            }
            return followUps;
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
}
