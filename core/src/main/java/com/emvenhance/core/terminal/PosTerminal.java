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
        Transaction transaction = current.get();
        OnlineRequest pinRequired = transaction != null ? transaction.pinRequired() : null;
        TransactionConfig sent;
        OnlineRequest request;
        if (pinRequired != null) {
            // Single Tap: the same sale again, now with the PIN — the same request and id
            sent = config.withAsyncRequestId(pinRequired.sent.getAsyncRequestId());
            request = pinRequired;
        } else {
            sent = config.withAsyncRequestId(UUID.randomUUID().toString());
            request = transaction != null ? transaction.wentOnline(sent) : null;
        }
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
         * If the host approved the last request, it is closed: acknowledged when the card
         * approved, reversed when the card declined.
         */
        private void closeLast(TransactionStep closing) {
            OnlineRequest last = online.isEmpty() ? null : online.get(online.size() - 1);
            if (last != null && last.answer != null && last.answer.isApproved()) {
                closeAtHost(last, closing);
            }
        }

        /**
         * ENDING: the EMV has returned — its reader closed, its kernel done — so the closings at
         * the host start only now. The last request by the decision (APPROVED → acknowledged,
         * DECLINED → reversed, if the host approved it); then whatever is still open is reversed —
         * after a cancel (it can come while the bank's call is in flight, so the answer is known
         * only now), a failure, or an earlier attempt's request. Then the terminal is freed and the
         * end delivered. Freed first: published from inside a step's case, the end reaches the
         * screen before a case of its own would run, and a new sale started then must not find
         * the terminal busy.
         */
        private void onEnding() {
            TransactionStepEvent decision = decision();
            if (decision.getStep() == TransactionStep.APPROVED) {
                closeLast(TransactionStep.ACKNOWLEDGING);
            } else if (decision.getStep() == TransactionStep.DECLINED) {
                closeLast(TransactionStep.REVERSING);
            }
            for (OnlineRequest request : online) {
                if (!closed(request) && needsReversal(request)) {
                    closeAtHost(request, TransactionStep.REVERSING);
                }
            }
            current.compareAndSet(this, null);
            TransactionStep end = decision.getStep().endOfDecision();
            publish(end == TransactionStep.ERROR
                    ? errorEvent(decision.getString(TransactionStepEvent.KEY_ERROR))
                    : TransactionStepEvent.of(end));
        }

        /** Closed already: an ACKNOWLEDGING / REVERSING with its request id is in the steps. */
        private boolean closed(OnlineRequest request) {
            String id = request.sent.getAsyncRequestId();
            for (TransactionStepEvent e : steps.getValues(new TransactionStepEvent[0])) {
                TransactionStep step = e.getStep();
                if ((step == TransactionStep.ACKNOWLEDGING || step == TransactionStep.REVERSING)
                        && id.equals(e.get(TransactionStepEvent.KEY_REQUEST_ID))) {
                    return true;
                }
            }
            return false;
        }

        /** A request still open at the end is reversed unless the host could not have approved it. */
        private boolean needsReversal(OnlineRequest request) {
            AuthResult answer = request.answer;
            String id = request.sent.getAsyncRequestId();
            if (answer != null && answer.isNotSent()) {
                apduTrace().note("HOST", "no reversal for request " + id
                        + ": the host could not have approved it — " + answer.getMessage());
                return false;
            }
            if (answer == null || answer.isApproved() || answer.isFailed()) {
                apduTrace().note("HOST", "REVERSAL for request " + id + ": "
                        + (answer == null ? "no host answer"
                                : answer.isFailed() ? "no usable host answer — "
                                        + answer.getMessage()
                                        : "approved, not completed"));
                return true;
            }
            return false;
        }

        /** One request closed at the host: ACKNOWLEDGING / REVERSING, the call, CLOSING_COMPLETED. */
        private void closeAtHost(OnlineRequest request, TransactionStep closing) {
            boolean acknowledge = closing == TransactionStep.ACKNOWLEDGING;
            String id = request.sent.getAsyncRequestId();
            String what = (acknowledge ? "Acknowledgement " : "Reversal ") + id;
            publish(TransactionStepEvent.builder(closing)
                    .message(what)
                    .put(TransactionStepEvent.KEY_REQUEST_ID, id)
                    .build());
            String failure = callHost(request.sent, acknowledge);
            String result = what + (failure == null ? " sent" : " failed — not retried: " + failure);
            EmvLog.d(result);
            apduTrace().note("HOST", result);
            publish(TransactionStepEvent.builder(TransactionStep.CLOSING_COMPLETED)
                    .message(result)
                    .put(TransactionStepEvent.KEY_REQUEST_ID, id)
                    .put(TransactionStepEvent.KEY_CLOSED_OK, failure == null)
                    .build());
        }

        /** The call at the host, waited for, not retried: null when it went through, else why not. */
        @Nullable
        private String callHost(TransactionConfig sent, boolean acknowledge) {
            try {
                (acknowledge ? communication.acknowledge(sent) : communication.reverse(sent))
                        .timeout(closeAtHostTimeoutMs(), TimeUnit.MILLISECONDS)
                        .blockingAwait();
                return null;
            } catch (RuntimeException e) {
                EmvLog.d("closing at the host failed: " + e);
                return e.getCause() instanceof TimeoutException
                        ? "the host didn't answer in time" : e.getMessage();
            }
        }

        OnlineRequest wentOnline(TransactionConfig sent) {
            OnlineRequest request = new OnlineRequest(sent);
            online.add(request);
            return request;
        }

        /** The last request, if the host answered it "PIN required" (Single Tap); else null. */
        @Nullable
        OnlineRequest pinRequired() {
            if (online.isEmpty()) {
                return null;
            }
            OnlineRequest last = online.get(online.size() - 1);
            return last.answer != null && last.answer.isPinRequired() ? last : null;
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
