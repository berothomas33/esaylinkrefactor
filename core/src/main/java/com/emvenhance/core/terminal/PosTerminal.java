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

public abstract class PosTerminal {

    protected final AbstractEmvBehavior behavior;

    protected final CommunicationBehavior communication;
    protected final PrinterBehavior printer;

    private final Executor transactionExecutor = Executors.newSingleThreadExecutor();

    private final Scheduler printScheduler =
            Schedulers.from(Executors.newSingleThreadExecutor());

    private final Scheduler followUpScheduler =
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

    public CommunicationBehavior communication() {
        return communication;
    }

    public PrinterBehavior printer() {
        return printer;
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

    protected long onlineTimeoutMs() {
        return 90_000;
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
        transactionExecutor.execute(transaction::run);
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

    public final boolean isSearchCancelled() {
        Transaction transaction = current.get();
        return transaction != null && !transaction.isRunning();
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

    @Nullable
    public abstract CardPresence searchCard(TransactionConfig config, CardSearchListener listener);

    protected abstract void cancelCardSearch();

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
            synchronized (steps) {
                if (end != null) {
                    return;
                }
                end = TransactionStepEvent.of(TransactionStep.CANCELLED);
                deliver(TransactionStepEvent.of(TransactionStep.CANCELLING,
                        "Cancelling — finishing with the card"));
            }
            cancelCardSearch();
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
                if (event.getStep() == TransactionStep.APPROVED) {
                    approvedReported = true;
                }
                deliver(event);
            }
        }

        private void deliver(TransactionStepEvent event) {
            EmvLog.d("TransactionStep: " + event);
            traceTransactionStep(event);
            steps.onNext(event);
        }

        private void run() {
            try {
                behavior.engine().begin();
                publish(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
                TransactionConfig attemptConfig = config;
                while (attemptConfig != null && isRunning()) {
                    attemptConfig = attempt(attemptConfig);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                publish(errorEvent(t.getMessage() != null ? t.getMessage() : "Transaction failed"));
            } finally {
                List<Runnable> followUps;
                synchronized (steps) {
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

        OnlineRequest wentOnline(TransactionConfig sent) {
            OnlineRequest request = new OnlineRequest(sent);
            online.add(request);
            return request;
        }

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

        @Nullable
        private TransactionConfig attempt(TransactionConfig config) {
            if (!behavior.prepare(config, this)) {
                publish(errorEvent("Terminal initialization failed"));
                return null;
            }
            searchCard(config, new EngineReportingListener(config));
            TransactionConfig retry = behavior.engine().consumePendingRetry();
            if (retry != null && !isRunning()) {
                EmvLog.d("Retry dropped: the transaction is over");
                apduTrace().note("EMV", "retry dropped — the transaction is over");
            }
            return retry;
        }

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

    private static final class OnlineRequest {

        final TransactionConfig sent;
        @Nullable
        volatile AuthResult answer;

        OnlineRequest(TransactionConfig sent) {
            this.sent = sent;
        }
    }

    private void sendFollowUp(String what, Completable call) {
        call.timeout(onlineTimeoutMs(), TimeUnit.MILLISECONDS)
                .subscribeOn(followUpScheduler)
                .subscribe(() -> EmvLog.d(what + " sent"),
                        e -> EmvLog.d(what + " failed: " + e));
    }

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
