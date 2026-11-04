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
        behavior.attach(this::goOnline);
        initializeVendor();
    }

    public AuthResult goOnline(TransactionConfig config) {
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
        @Nullable
        private volatile TransactionStepEvent end;
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
            if (end != null) {
                return;
            }
            synchronized (steps) {
                if (end != null) {
                    return;
                }
                end = TransactionStepEvent.of(TransactionStep.CANCELLED);
                deliver(cancelling);
            }
        }

        boolean isRunning() {
            return end == null;
        }

        boolean isCancelled() {
            TransactionStepEvent e = end;
            return e != null && e.getStep() == TransactionStep.CANCELLED;
        }

        void publish(TransactionStepEvent event) {
            if (end != null) {
                dropped(event);
                return;
            }
            synchronized (steps) {
                if (end != null) {
                    dropped(event);
                    return;
                }
                deliver(event);
            }
        }

        void end(TransactionStepEvent end) {
            if (this.end != null) {
                dropped(end);
                return;
            }
            synchronized (steps) {
                if (this.end != null) {
                    dropped(end);
                    return;
                }
                this.end = end;
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
                outcome(outcome);
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

        private boolean delivered(TransactionStep step) {
            for (TransactionStepEvent e : steps.getValues(new TransactionStepEvent[0])) {
                if (e.getStep() == step) {
                    return true;
                }
            }
            return false;
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
                case ACKNOWLEDGING:
                    onAcknowledging(event);
                    break;
                case REVERSING:
                    onReversing(event);
                    break;
                case CLOSING_COMPLETED:
                    onClosingCompleted(event);
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
                    if (end == null) {
                        end = errorEvent("Transaction ended without an outcome");
                    }
                    deliver(TransactionStepEvent.of(TransactionStep.ENDING));
                }
            }
        }

        private void outcome(TransactionStepEvent outcome) {
            synchronized (steps) {
                if (end != null) {
                    EmvLog.d("EMV outcome dropped, the transaction has ended: " + outcome);
                    apduTrace().note("EMV", "EMV outcome dropped, the transaction has ended ("
                            + end.getStep() + "): " + outcome.getStep());
                    return;
                }
                apduTrace().note("EMV", "EMV finished: " + outcome.getStep()
                        + " — the transaction completes");
                deliver(outcome);
                end = TransactionStepEvent.of(TransactionStep.COMPLETED);
            }
        }

        private void onCancelling() {
            behavior.cancelCardSearch();
        }

        private void onEnding() {
            closeNext(0);
        }

        private void onClosingCompleted(TransactionStepEvent completed) {
            String id = completed.get(TransactionStepEvent.KEY_REQUEST_ID);
            int next = online.size();
            for (int i = 0; i < online.size(); i++) {
                if (online.get(i).sent.getAsyncRequestId().equals(id)) {
                    next = i + 1;
                    break;
                }
            }
            closeNext(next);
        }

        private void closeNext(int from) {
            synchronized (steps) {
                TransactionStepEvent closing = nextClosing(from);
                if (closing != null) {
                    deliver(closing);
                    return;
                }
                current.compareAndSet(this, null);
                deliver(end);
            }
        }

        private void onAcknowledging(TransactionStepEvent step) {
            closeAtHost(step, communication::acknowledge);
        }

        private void onReversing(TransactionStepEvent step) {
            closeAtHost(step, communication::reverse);
        }

        private void closeAtHost(TransactionStepEvent step,
                Function<TransactionConfig, Completable> call) {
            String what = step.getMessage();
            TransactionConfig sent = sentRequest(step);
            if (sent == null) {
                closingCompleted(step, false, what + " not sent: no such request");
                return;
            }
            try {
                call.apply(sent).timeout(closeAtHostTimeoutMs(), TimeUnit.MILLISECONDS)
                        .blockingAwait();
                EmvLog.d(what + " sent");
                apduTrace().note("HOST", what + " sent");
                closingCompleted(step, true, what + " sent");
            } catch (RuntimeException e) {
                String failed = what + " failed — not retried: "
                        + (e.getCause() instanceof TimeoutException
                                ? "the host didn't answer in time" : e.getMessage());
                EmvLog.d(what + " failed: " + e);
                apduTrace().note("HOST", failed);
                closingCompleted(step, false, failed);
            }
        }

        private void closingCompleted(TransactionStepEvent step, boolean ok, String message) {
            synchronized (steps) {
                deliver(TransactionStepEvent.builder(TransactionStep.CLOSING_COMPLETED)
                        .message(message)
                        .put(TransactionStepEvent.KEY_REQUEST_ID,
                                step.getString(TransactionStepEvent.KEY_REQUEST_ID))
                        .put(TransactionStepEvent.KEY_CLOSED_OK, ok)
                        .build());
            }
        }

        @Nullable
        private TransactionConfig sentRequest(TransactionStepEvent closing) {
            String id = closing.get(TransactionStepEvent.KEY_REQUEST_ID);
            for (OnlineRequest request : online) {
                if (request.sent.getAsyncRequestId().equals(id)) {
                    return request.sent;
                }
            }
            apduTrace().note("HOST", closing.getStep() + " not sent: no request " + id);
            return null;
        }

        private static TransactionStepEvent closeAtHostStep(TransactionStep step, String what,
                String id) {
            return TransactionStepEvent.builder(step)
                    .message(what + " " + id)
                    .put(TransactionStepEvent.KEY_REQUEST_ID, id)
                    .build();
        }

        OnlineRequest wentOnline(TransactionConfig sent) {
            OnlineRequest request = new OnlineRequest(sent);
            online.add(request);
            return request;
        }

        @Nullable
        private TransactionStepEvent nextClosing(int from) {
            boolean completedApproved = end.getStep() == TransactionStep.COMPLETED
                    && delivered(TransactionStep.APPROVED);
            for (int i = from; i < online.size(); i++) {
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
