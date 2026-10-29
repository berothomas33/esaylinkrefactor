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
        behavior.attach(new EmvEngine(this::authorize));
        initializeVendor();
    }

    public AuthResult authorize(TransactionConfig config) {
        publish(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        publish(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));
        TransactionConfig sent = config.withAsyncRequestId(UUID.randomUUID().toString());
        Transaction transaction = current.get();
        OnlineRequest request = transaction != null ? transaction.wentOnline(sent) : null;
        AuthResult result;
        try {
            result = communication.authorize(sent)
                    .timeout(onlineTimeoutMs(), TimeUnit.MILLISECONDS)
                    .blockingGet();
        } catch (Exception e) {
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

    protected long onlineTimeoutMs() {
        return 90_000;
    }

    protected long followUpTimeoutMs() {
        return 15_000;
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

    public final void setApduLoggingEnabled(boolean enabled) {
        apduTrace().setEnabled(enabled);
    }

    public final boolean isApduLoggingEnabled() {
        return apduTrace().isEnabled();
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
        private boolean approvedReported;

        private Transaction(TransactionConfig config) {
            this.config = config;
        }

        public Observable<TransactionStepEvent> steps() {
            return Observable.wrap(observer -> steps.subscribe(new SafeObserver<>(observer)));
        }

        public void cancel() {
            TransactionStepEvent cancelling = TransactionStepEvent.of(TransactionStep.CANCELLING,
                    "Cancelling — finishing with the card");
            synchronized (steps) {
                if (end != null) {
                    return;
                }
                end = TransactionStepEvent.of(TransactionStep.CANCELLED);
                deliver(cancelling);
            }
            dispatchTransactionStep(cancelling);
        }

        boolean isRunning() {
            return end == null;
        }

        boolean isCancelled() {
            TransactionStepEvent e = end;
            return e != null && e.getStep() == TransactionStep.CANCELLED;
        }

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

        private void deliver(TransactionStepEvent event) {
            if (event.getStep() == TransactionStep.APPROVED) {
                approvedReported = true;
            }
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
                case ACKNOWLEDGING:
                    onAcknowledging(event);
                    break;
                case REVERSING:
                    onReversing(event);
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
                publish(errorEvent(t.getMessage() != null ? t.getMessage() : "Transaction failed"));
            } finally {
                onEnd();
            }
        }

        private void onCancelling() {
            behavior.cancelCardSearch();
        }

        private void onEnd() {
            List<TransactionStepEvent> followUps;
            synchronized (steps) {
                if (end == null) {
                    end = errorEvent("Transaction ended without an outcome");
                }
                followUps = closeOnline();
            }
            for (TransactionStepEvent followUp : followUps) {
                synchronized (steps) {
                    deliver(followUp);
                }
                dispatchTransactionStep(followUp);
            }
            synchronized (steps) {
                current.compareAndSet(this, null);
                deliver(end);
            }
        }

        private void onAcknowledging(TransactionStepEvent acknowledging) {
            TransactionConfig sent = sentRequest(acknowledging);
            if (sent == null) {
                return;
            }
            sendFollowUp(acknowledging.getMessage(),
                    Completable.defer(() -> communication.acknowledge(sent)));
        }

        private void onReversing(TransactionStepEvent reversing) {
            TransactionConfig sent = sentRequest(reversing);
            if (sent == null) {
                return;
            }
            sendFollowUp(reversing.getMessage(),
                    Completable.defer(() -> communication.reverse(sent)));
        }

        @Nullable
        private TransactionConfig sentRequest(TransactionStepEvent followUp) {
            String id = followUp.get(TransactionStepEvent.KEY_REQUEST_ID);
            for (OnlineRequest request : online) {
                if (request.sent.getAsyncRequestId().equals(id)) {
                    return request.sent;
                }
            }
            apduTrace().note("HOST", followUp.getStep() + " not sent: no request " + id);
            return null;
        }

        private static TransactionStepEvent followUpStep(TransactionStep step, String what,
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

        private List<TransactionStepEvent> closeOnline() {
            List<TransactionStepEvent> followUps = new ArrayList<>();
            boolean completedApproved = approvedReported
                    && end.getStep() == TransactionStep.COMPLETED;
            for (int i = 0; i < online.size(); i++) {
                OnlineRequest request = online.get(i);
                AuthResult answer = request.answer;
                boolean last = i == online.size() - 1;
                String id = request.sent.getAsyncRequestId();
                if (answer != null && answer.isApproved() && last && completedApproved) {
                    apduTrace().note("HOST", "ACKNOWLEDGEMENT for request " + id);
                    followUps.add(
                            followUpStep(TransactionStep.ACKNOWLEDGING, "Acknowledgement", id));
                } else if (answer != null && answer.isNotSent()) {
                    apduTrace().note("HOST", "no reversal for request " + id
                            + ": the host could not have approved it — " + answer.getMessage());
                } else if (answer == null || answer.isApproved() || answer.isFailed()) {
                    apduTrace().note("HOST", "REVERSAL for request " + id + ": "
                            + (answer == null ? "no host answer"
                                    : answer.isFailed() ? "no usable host answer — "
                                            + answer.getMessage()
                                            : "approved, not completed"));
                    followUps.add(followUpStep(TransactionStep.REVERSING, "Reversal", id));
                }
            }
            return followUps;
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

    private void sendFollowUp(String what, Completable call) {
        try {
            call.timeout(followUpTimeoutMs(), TimeUnit.MILLISECONDS).blockingAwait();
            EmvLog.d(what + " sent");
            apduTrace().note("HOST", what + " sent");
        } catch (RuntimeException e) {
            EmvLog.d(what + " failed: " + e);
            apduTrace().note("HOST", what + " failed — not retried: "
                    + (e.getCause() instanceof TimeoutException
                            ? "the host didn't answer in time" : e.getMessage()));
        }
    }

    static TransactionStepEvent errorEvent(String error) {
        return TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error)
                .put(TransactionStepEvent.KEY_ERROR, error)
                .build();
    }
}
