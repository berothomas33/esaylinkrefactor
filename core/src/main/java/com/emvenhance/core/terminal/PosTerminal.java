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
            publish(TransactionStepEvent.of(TransactionStep.CANCELLING,
                    "Cancelling — finishing with the card"));
        }

        /** Ends the transaction with an error: FAILED now, ERROR with the same message at the end. */
        void fail(String message) {
            publish(TransactionStepEvent.builder(TransactionStep.FAILED)
                    .message(message)
                    .put(TransactionStepEvent.KEY_ERROR, message)
                    .build());
        }

        /**
         * The one way in, for every step; its case in dispatchTransactionStep runs as it is
         * delivered. After the decision only the steps that follow it pass (ENDING, the closings,
         * the end): a second decision, or a report from code still unwinding, is dropped.
         * Checked before the lock: the closings call the host while holding it, and a cancel on a
         * decided transaction must not wait for them
         * (PosTerminalCloseAtHostTest.aCancelDuringASlowAcknowledgementDoesNotWaitForIt).
         * Checked again under it: a cancel and an outcome at the same moment — only one goes in.
         */
        void publish(TransactionStepEvent event) {
            if (droppedAfterDecision(event)) {
                return;
            }
            synchronized (steps) {
                if (droppedAfterDecision(event)) {
                    return;
                }
                EmvLog.d("TransactionStep: " + event);
                traceTransactionStep(event);
                steps.onNext(event);
            }
        }

        private boolean droppedAfterDecision(TransactionStepEvent event) {
            if (event.getStep().isAfterDecision() || isRunning()) {
                return false;
            }
            EmvLog.d("TransactionStep dropped, the transaction has ended: " + event);
            apduTrace().note("EMV",
                    "transaction step dropped, the transaction has ended: " + event);
            return true;
        }

        /**
         * The transaction's state is its steps: the first decision it delivered (CANCELLING,
         * APPROVED, DECLINED or FAILED), or null while it runs. No field keeps a copy.
         * A decision is visible here before its step's case runs: ReplaySubject.onNext stores
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

        boolean isRunning() {
            return decision() == null;
        }

        boolean isCancelled() {
            TransactionStepEvent d = decision();
            return d != null && d.getStep() == TransactionStep.CANCELLING;
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
                    closeFrom(0);
                    break;
                case ACKNOWLEDGING:
                case REVERSING:
                    onClosing(event);
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
                fail(t.getMessage() != null ? t.getMessage() : "Transaction failed");
            } finally {
                if (isRunning()) {
                    fail("Transaction ended without an outcome");
                }
                publish(TransactionStepEvent.of(TransactionStep.ENDING));
            }
        }

        private void onCancelling() {
            behavior.cancelCardSearch();
        }

        /**
         * The closings at the host, one step each, in order (ENDING starts them at 0): the next
         * request that needs an acknowledgement or a reversal gets its step; none left → the
         * terminal is freed and the end delivered. Freed before the end is published: published
         * from inside another step's case, the end reaches the screen before a case of its own
         * would run, and a new sale started then must not find the terminal busy.
         */
        private void closeFrom(int from) {
            TransactionStepEvent decision = decision();
            boolean approved = decision.getStep() == TransactionStep.APPROVED;
            for (int i = from; i < online.size(); i++) {
                TransactionStepEvent closing = closingFor(i, approved);
                if (closing != null) {
                    publish(closing);
                    return;
                }
            }
            current.compareAndSet(this, null);
            TransactionStep end = decision.getStep().endOfDecision();
            publish(end == TransactionStep.ERROR
                    ? errorEvent(decision.getString(TransactionStepEvent.KEY_ERROR))
                    : TransactionStepEvent.of(end));
        }

        /** ACKNOWLEDGING / REVERSING: the call at the host, waited for; then the next closing. */
        private void onClosing(TransactionStepEvent closing) {
            int index = closing.get(TransactionStepEvent.KEY_REQUEST_INDEX);
            Function<TransactionConfig, Completable> call =
                    closing.getStep() == TransactionStep.ACKNOWLEDGING
                            ? communication::acknowledge : communication::reverse;
            String what = closing.getMessage();
            boolean ok;
            String result;
            try {
                call.apply(online.get(index).sent)
                        .timeout(closeAtHostTimeoutMs(), TimeUnit.MILLISECONDS)
                        .blockingAwait();
                ok = true;
                result = what + " sent";
                EmvLog.d(result);
            } catch (RuntimeException e) {
                ok = false;
                result = what + " failed — not retried: "
                        + (e.getCause() instanceof TimeoutException
                                ? "the host didn't answer in time" : e.getMessage());
                EmvLog.d(what + " failed: " + e);
            }
            apduTrace().note("HOST", result);
            publish(TransactionStepEvent.builder(TransactionStep.CLOSING_COMPLETED)
                    .message(result)
                    .put(TransactionStepEvent.KEY_REQUEST_ID,
                            closing.getString(TransactionStepEvent.KEY_REQUEST_ID))
                    .put(TransactionStepEvent.KEY_CLOSED_OK, ok)
                    .build());
            closeFrom(index + 1);
        }

        OnlineRequest wentOnline(TransactionConfig sent) {
            OnlineRequest request = new OnlineRequest(sent);
            online.add(request);
            return request;
        }

        /** ACKNOWLEDGING, REVERSING, or null (nothing to close) for the request at {@code i}. */
        @Nullable
        private TransactionStepEvent closingFor(int i, boolean approved) {
            OnlineRequest request = online.get(i);
            AuthResult answer = request.answer;
            boolean last = i == online.size() - 1;
            String id = request.sent.getAsyncRequestId();
            if (answer != null && answer.isApproved() && last && approved) {
                apduTrace().note("HOST", "ACKNOWLEDGEMENT for request " + id);
                return closingStep(TransactionStep.ACKNOWLEDGING, "Acknowledgement", id, i);
            } else if (answer != null && answer.isNotSent()) {
                apduTrace().note("HOST", "no reversal for request " + id
                        + ": the host could not have approved it — " + answer.getMessage());
            } else if (answer == null || answer.isApproved() || answer.isFailed()) {
                apduTrace().note("HOST", "REVERSAL for request " + id + ": "
                        + (answer == null ? "no host answer"
                                : answer.isFailed() ? "no usable host answer — "
                                        + answer.getMessage()
                                        : "approved, not completed"));
                return closingStep(TransactionStep.REVERSING, "Reversal", id, i);
            }
            return null;
        }

        private static TransactionStepEvent closingStep(TransactionStep step, String what,
                String id, int index) {
            return TransactionStepEvent.builder(step)
                    .message(what + " " + id)
                    .put(TransactionStepEvent.KEY_REQUEST_ID, id)
                    .put(TransactionStepEvent.KEY_REQUEST_INDEX, index)
                    .build();
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
