package com.emvenhance.core;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs one EMV transaction and publishes everything the UI needs.
 *
 * <p>{@link #state()} is a {@code BehaviorSubject}: a sticky snapshot, so a screen that
 * subscribes late or after rotation immediately sees where the transaction stands.
 *
 * <p>{@link #events()} is a {@code PublishSubject}: one-shot prompts such as dialogs and
 * the receipt, which must not be replayed to a new subscriber.
 *
 * <p>Both emit on the kernel's background thread; subscribers decide where to observe.
 */
public final class EmvTransaction {

    private final EmvBehavior emv;
    private final PrinterBehavior printer;
    private final CommunicationBehavior communication;

    private final BehaviorSubject<EmvState> state = BehaviorSubject.createDefault(EmvState.idle());
    private final PublishSubject<EmvEvent> events = PublishSubject.create();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private EmvStep step;
    private String mode;
    private String pan;
    private String issuerName;
    private long amountMinor;

    public EmvTransaction(EmvBehavior emv, PrinterBehavior printer,
            CommunicationBehavior communication) {
        this.emv = emv;
        this.printer = printer;
        this.communication = communication;
    }

    public Observable<EmvState> state() {
        return state.hide();
    }

    public Observable<EmvEvent> events() {
        return events.hide();
    }

    public void startContact(String procCode, long amountMinor) {
        start(procCode, amountMinor, true);
    }

    public void startContactless(String procCode, long amountMinor) {
        start(procCode, amountMinor, false);
    }

    private void start(String procCode, long amount, boolean contact) {
        if (!running.compareAndSet(false, true)) {
            events.onNext(EmvEvent.message("A transaction is already running"));
            return;
        }
        amountMinor = amount;
        mode = contact ? "Contact" : "Contactless";
        pan = null;
        issuerName = null;

        goTo(EmvStep.TERMINAL_INITIALIZATION, null);
        if (!emv.prepare(procCode, amount, contact, !contact)) {
            finish(false, "Terminal initialization failed", null);
            return;
        }

        goTo(EmvStep.SEARCH_CARD, null);
        if (contact) {
            emv.startContact(callback);
        } else {
            emv.startContactless(callback);
        }
    }

    private final EmvBehavior.Callback callback = new EmvBehavior.Callback() {

        @Override
        public void onStep(EmvStep newStep, @Nullable String detail) {
            goTo(newStep, detail);
        }

        @Override
        public void onConfirmCard(String cardPan, String issuer, String cardHolderName) {
            pan = cardPan;
            issuerName = issuer;
            goTo(EmvStep.READ_APPLICATION_DATA, "card read");
            events.onNext(EmvEvent.confirmCard(cardPan, issuer, cardHolderName));
        }

        @Override
        public void onAskPin(boolean onlinePin, boolean bypassAllowed, int leftTimes) {
            goTo(onlinePin ? EmvStep.CARDHOLDER_VERIFICATION : EmvStep.OFFLINE_PIN_VERIFICATION,
                    null);
            events.onNext(EmvEvent.askPin(onlinePin, bypassAllowed, leftTimes));
        }

        @Override
        public void onNeedOnline() {
            goTo(EmvStep.START_ONLINE_PROCESS, null);
            events.onNext(EmvEvent.needOnline());
            boolean approved = communication.authorize(pan, amountMinor);
            goTo(EmvStep.ISSUER_AUTHENTICATION, approved ? "host approved" : "host declined");
        }

        @Override
        public void onRemoveCard() {
            events.onNext(EmvEvent.removeCard());
        }

        @Override
        public void onSecondTap() {
            events.onNext(EmvEvent.secondTap());
        }

        @Override
        public void onSeePhone() {
            events.onNext(EmvEvent.seePhone());
        }

        @Override
        public void onApproved(String result) {
            finish(true, result, null);
        }

        @Override
        public void onFailed(String title, @Nullable String reason) {
            finish(false, title, reason);
        }
    };

    private void goTo(EmvStep newStep, @Nullable String detail) {
        step = newStep;
        state.onNext(new EmvState(true, step, mode, pan, issuerName, null, null));
        events.onNext(EmvEvent.stepChanged(newStep, detail));
    }

    private void finish(boolean approved, String label, @Nullable String reason) {
        // A kernel can report an outcome more than once (an error, then the result check),
        // so only the first outcome counts.
        if (!running.compareAndSet(true, false)) {
            return;
        }
        goTo(EmvStep.TRANSACTION_COMPLETION, label);

        state.onNext(new EmvState(false, step, mode, pan, issuerName,
                approved ? label : null, approved ? null : label));

        // A null PAN means no card was ever read (e.g. initialization failed), so there is
        // no transaction to print a receipt for.
        if (pan != null) {
            boolean printed = printer.print(receiptLines(approved, label));
            events.onNext(
                    EmvEvent.receipt(approved, printed ? "Receipt printed" : "Printer failed"));
        }
        events.onNext(approved ? EmvEvent.completed(label) : EmvEvent.failed(label, reason));
    }

    private List<String> receiptLines(boolean approved, String label) {
        List<String> lines = new ArrayList<>();
        lines.add("==== EMV RECEIPT ====");
        lines.add(approved ? "APPROVED" : "DECLINED");
        lines.add("MODE: " + (mode != null ? mode : "N/A"));
        lines.add("PAN: " + (pan != null ? pan : "N/A"));
        lines.add("RESULT: " + label);
        lines.add("=====================");
        return lines;
    }
}
