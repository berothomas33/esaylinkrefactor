package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.transaction.TransactionType;
import com.emvenhance.core.emv.EmvStepEvent;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.transaction.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.param.EmvParams;
import com.emvenhance.core.emv.ApduTrace;
import com.emvenhance.core.util.EmvLog;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.observers.DisposableObserver;
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
import java.util.function.Function;

public abstract class PosTerminal {

    protected final AbstractEmvBehavior behavior;

    protected final CommunicationBehavior communication;
    protected final PrinterBehavior printer;

    private final Scheduler transactionScheduler =
            Schedulers.from(Executors.newSingleThreadExecutor());

    private final Scheduler printScheduler =
            Schedulers.from(Executors.newSingleThreadExecutor());

    private final AtomicReference<Transaction> current = new AtomicReference<>();

    protected PosTerminal(AbstractEmvBehavior behavior,
            CommunicationBehavior communication,
            PrinterBehavior printer) {
        this.behavior = behavior;
        this.communication = communication;
        this.printer = printer;
        behavior.attach(this::sendToBank);
        initializeVendor();
    }

    public AuthResult sendToBank(TransactionConfig config) {
        publish(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        publish(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));
        TransactionConfig sent = config.withAsyncRequestId(UUID.randomUUID().toString());
        Transaction transaction = current.get();
        OnlineRequest request = transaction != null ? transaction.wentOnline(sent) : null;
        AuthResult result;
        try {
            result = communication.connectToHost(sent)
                    .timeout(onlineTimeoutMs(), TimeUnit.MILLISECONDS)
                    .blockingGet();
        } catch (Exception e) {
            result = failureOf(e);
        }
        if (request != null) {
            request.answer = result;
        }
        publish(TransactionStepEvent.builder(TransactionStep.ONLINE_COMPLETED)
                .put(TransactionStepEvent.KEY_RESULT, result.getMessage())
                .build());
        return result;
    }

    private static AuthResult failureOf(Exception e) {
        if (e.getCause() instanceof TimeoutException) {
            return AuthResult.failed("Online failed: the host didn't answer in time");
        }
        return AuthResult.failed(e.getMessage() != null ? e.getMessage() : "Online failed");
    }

    protected long onlineTimeoutMs() {
        return 90_000;
    }

    protected long closeAtHostTimeoutMs() {
        return 60_000;
    }

    public void printReceipt(List<String> lines) {
        printer.print(lines)
                .subscribeOn(printScheduler)
                .subscribe(() -> { }, Throwable::printStackTrace);
    }

    public Observable<EmvStepEvent> emvSteps() {
        return behavior.engine().emvSteps();
    }

    public ApduTrace apduTrace() {
        return behavior.engine().apduTrace();
    }

    public final Transaction acceptCard(TransactionType type, long amountMinor) {
        return acceptCard(type, amountMinor, null);
    }

    public final Transaction acceptCard(TransactionType type, long amountMinor,
            @Nullable String referenceNumber) {
        return startTransaction(new TransactionConfig(type, amountMinor, EntryMethod.ANY)
                .withReferenceNumber(referenceNumber));
    }

    public Transaction startTransaction(TransactionConfig config) {
        Transaction transaction = new Transaction(config);
        if (!current.compareAndSet(null, transaction)) {
            EmvLog.d("POS terminal is busy: the previous transaction hasn't finished");
            throw new TerminalBusyException();
        }
        transaction.begin();
        return transaction;
    }

    public void dispose() {
        Transaction transaction = current.get();
        if (transaction != null) {
            transaction.cancel();
        }
        behavior.dispose();
    }

    public final boolean isBusy() {
        return current.get() != null;
    }

    private void publish(TransactionStepEvent event) {
        Transaction transaction = current.get();
        if (transaction == null) {
            EmvLog.d("TransactionStep dropped, no transaction: " + event);
            return;
        }
        transaction.publish(event);
    }

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

    public String serialNumber() {
        return "";
    }

    public String[] requiredPermissions() {
        return new String[0];
    }

    public List<String> countries() {
        return Collections.emptyList();
    }

    public String setCountry(String country) {
        throw new UnsupportedOperationException("This terminal has no country setting");
    }

    @Nullable
    public EmvParams emvParams() {
        return null;
    }

    protected void initializeVendor() {
    }

    public final class Transaction {

        private final TransactionConfig config;
        // The steps ARE the transaction's state (see decision()): keep every step — a
        // ReplaySubject with no size or time limit. A PublishSubject, or a bounded replay,
        // would forget the decision and the transaction would look like it is still running.
        private final ReplaySubject<TransactionStepEvent> steps = ReplaySubject.create();
        private final List<OnlineRequest> online = new ArrayList<>();

        private Transaction(TransactionConfig config) {
            this.config = config;
            steps.subscribe(new TransactionStepObserver());
        }

        private final class TransactionStepObserver extends DisposableObserver<TransactionStepEvent> {

            @Override
            public void onNext(TransactionStepEvent event) {
                dispatchTransactionStep(event);
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        }

        public Observable<TransactionStepEvent> steps() {
            return Observable.wrap(observer -> steps.subscribe(new SafeObserver<>(observer)));
        }

        public void cancel() {
            TransactionStepEvent cancelling = TransactionStepEvent.of(TransactionStep.CANCELLING,
                    "Cancelling — finishing with the card");
            decide(cancelling);
        }

        /**
         * The transaction's state is its steps: the first decision it delivered (CANCELLING,
         * APPROVED, DECLINED or FAILED), or null while it runs. No field keeps a copy.
         * A decision is visible here before its step's method runs: ReplaySubject.onNext stores
         * the step first, then emits it — so onCancelling already sees isCancelled().
         */
        @Nullable
        private TransactionStepEvent decision() {
            for (TransactionStepEvent e : steps.getValues(new TransactionStepEvent[0])) {
                if (e.getStep().isDecision()) {
                    return e;
                }
            }
            return null;
        }

        // cancel / publish / decide look at decision() before taking the lock on purpose: the
        // closings (ACKNOWLEDGING / REVERSING) call the host while holding it, and a decided
        // transaction must not wait for them
        // (PosTerminalCloseAtHostTest.aCancelDuringASlowAcknowledgement…).

        boolean isRunning() {
            return decision() == null;
        }

        boolean isCancelled() {
            TransactionStepEvent d = decision();
            return d != null && d.getStep() == TransactionStep.CANCELLING;
        }

        void publish(TransactionStepEvent event) {
            if (decision() != null) {
                dropped(event);
                return;
            }
            synchronized (steps) {
                if (decision() != null) {
                    dropped(event);
                    return;
                }
                deliver(event);
            }
        }

        /** Ends the transaction with an error: FAILED now, ERROR as its end. */
        void end(TransactionStepEvent error) {
            String message = error.getMessage() != null ? error.getMessage() : "Error";
            decide(TransactionStepEvent.builder(TransactionStep.FAILED)
                    .message(message)
                    .put(TransactionStepEvent.KEY_ERROR, message)
                    .build());
        }

        /** The first decision wins; anything after it is dropped. */
        private void decide(TransactionStepEvent decision) {
            if (decision() != null) {
                dropped(decision);
                return;
            }
            synchronized (steps) {
                if (decision() != null) {
                    dropped(decision);
                    return;
                }
                apduTrace().note("EMV", "the transaction is decided: " + decision.getStep());
                deliver(decision);
            }
        }

        private void dropped(TransactionStepEvent event) {
            EmvLog.d("TransactionStep dropped, the transaction has ended: " + event);
            apduTrace().note("EMV",
                    "transaction step dropped, the transaction has ended: " + event);
        }

        void emvFinished(TransactionStepEvent outcome) {
            TransactionStep step = outcome.getStep();
            if (isOutcome(step)) {
                decide(outcome);
            } else if (step == TransactionStep.ERROR) {
                end(outcome);
            } else {
                apduTrace().note("EMV", "EMV finished with no outcome (" + step
                        + ") — the transaction ends with an error");
                end(errorEvent("EMV finished with no outcome: " + step));
            }
        }

        private boolean isOutcome(TransactionStep step) {
            return step == TransactionStep.APPROVED || step == TransactionStep.DECLINED;
        }

        private void deliver(TransactionStepEvent event) {
            EmvLog.d("TransactionStep: " + event);
            traceTransactionStep(event);
            steps.onNext(event);
        }

        private void begin() {
            publish(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        }

        private void dispatchTransactionStep(TransactionStepEvent event) {
            switch (event.getStep()) {
                case TRANSACTION_STARTED:
                    onTransactionStarted(event);
                    break;
                case CANCELLING:
                    onCancelling();
                    break;
                case ENDING:
                    onEnding();
                    break;
                default:
                    break;
            }
        }

        private void onTransactionStarted(TransactionStepEvent started) {
            transactionScheduler.scheduleDirect(() -> runTransaction(started));
        }

        private void runTransaction(TransactionStepEvent started) {
            try {
                behavior.engine().begin();
                traceTransactionStep(started);
                if (isRunning()) {
                    behavior.run(config, this);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                end(errorEvent(t.getMessage() != null ? t.getMessage() : "Transaction failed"));
            } finally {
                synchronized (steps) {
                    if (decision() == null) {
                        end(errorEvent("Transaction ended without an outcome"));
                    }
                    deliver(TransactionStepEvent.of(TransactionStep.ENDING));
                }
            }
        }

        private void onCancelling() {
            behavior.cancelCardSearch();
        }

        /**
         * The safe end: each request sent to the host is acknowledged or reversed, in order,
         * then the terminal is freed and the end delivered. ACKNOWLEDGING / REVERSING and
         * CLOSING_COMPLETED are reports the screen sees. Runs under the steps lock (ENDING is
         * delivered under it); an ended transaction's cancel / publish don't wait for it.
         */
        private void onEnding() {
            synchronized (steps) {
                TransactionStepEvent decision = decision();
                boolean completedApproved = decision.getStep() == TransactionStep.APPROVED;
                for (int i = 0; i < online.size(); i++) {
                    TransactionStepEvent closing = closingFor(i, completedApproved);
                    if (closing != null) {
                        closeAtHost(online.get(i).sent, closing);
                    }
                }
                current.compareAndSet(this, null);
                deliver(endOf(decision));
            }
        }

        private void closeAtHost(TransactionConfig sent, TransactionStepEvent closing) {
            deliver(closing);
            Function<TransactionConfig, Completable> call =
                    closing.getStep() == TransactionStep.ACKNOWLEDGING
                            ? communication::acknowledge : communication::reverse;
            String what = closing.getMessage();
            try {
                call.apply(sent).timeout(closeAtHostTimeoutMs(), TimeUnit.MILLISECONDS)
                        .blockingAwait();
                EmvLog.d(what + " sent");
                apduTrace().note("HOST", what + " sent");
                deliver(closingCompleted(closing, true, what + " sent"));
            } catch (RuntimeException e) {
                String failed = what + " failed — not retried: "
                        + (e.getCause() instanceof TimeoutException
                                ? "the host didn't answer in time" : e.getMessage());
                EmvLog.d(what + " failed: " + e);
                apduTrace().note("HOST", failed);
                deliver(closingCompleted(closing, false, failed));
            }
        }

        private static TransactionStepEvent closingCompleted(TransactionStepEvent closing,
                boolean ok, String message) {
            return TransactionStepEvent.builder(TransactionStep.CLOSING_COMPLETED)
                    .message(message)
                    .put(TransactionStepEvent.KEY_REQUEST_ID,
                            closing.getString(TransactionStepEvent.KEY_REQUEST_ID))
                    .put(TransactionStepEvent.KEY_CLOSED_OK, ok)
                    .build();
        }

        private static TransactionStepEvent closeAtHostStep(TransactionStep step, String what,
                String id) {
            return TransactionStepEvent.builder(step)
                    .message(what + " " + id)
                    .put(TransactionStepEvent.KEY_REQUEST_ID, id)
                    .build();
        }

        /** The end step a decision leads to; ERROR keeps the failure's message. */
        private TransactionStepEvent endOf(TransactionStepEvent decision) {
            TransactionStep end = decision.getStep().endOfDecision();
            return end == TransactionStep.ERROR
                    ? errorEvent(decision.getString(TransactionStepEvent.KEY_ERROR))
                    : TransactionStepEvent.of(end);
        }

        OnlineRequest wentOnline(TransactionConfig sent) {
            OnlineRequest request = new OnlineRequest(sent);
            online.add(request);
            return request;
        }

        /** ACKNOWLEDGING, REVERSING, or null (nothing to close) for the request at {@code i}. */
        @Nullable
        private TransactionStepEvent closingFor(int i, boolean completedApproved) {
            OnlineRequest request = online.get(i);
            AuthResult answer = request.answer;
            boolean last = i == online.size() - 1;
            String id = request.sent.getAsyncRequestId();
            if (answer != null && answer.isApproved() && last && completedApproved) {
                apduTrace().note("HOST", "ACKNOWLEDGEMENT for request " + id);
                return closeAtHostStep(TransactionStep.ACKNOWLEDGING, "Acknowledgement", id);
            } else if (answer != null && answer.isNotSent()) {
                apduTrace().note("HOST", "no reversal for request " + id
                        + ": the host could not have approved it — " + answer.getMessage());
            } else if (answer == null || answer.isApproved() || answer.isFailed()) {
                apduTrace().note("HOST", "REVERSAL for request " + id + ": "
                        + (answer == null ? "no host answer"
                                : answer.isFailed() ? "no usable host answer — "
                                        + answer.getMessage()
                                        : "approved, not completed"));
                return closeAtHostStep(TransactionStep.REVERSING, "Reversal", id);
            }
            return null;
        }
    }

    private static final class OnlineRequest {

        final TransactionConfig sent;
        @Nullable
        volatile AuthResult answer;

        OnlineRequest(TransactionConfig sent) {
            this.sent = sent;
        }
    }

    static TransactionStepEvent errorEvent(String error) {
        return TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error)
                .put(TransactionStepEvent.KEY_ERROR, error)
                .build();
    }
}
