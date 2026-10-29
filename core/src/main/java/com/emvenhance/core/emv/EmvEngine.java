package com.emvenhance.core.emv;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.util.EmvLog;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * The EMV side of one terminal: the {@link #emvSteps()} observable, the APDU trace and the way
 * online. No vendor logic, and nothing of the transaction: it holds no
 * transaction state and publishes no transaction step. Whether a step may still run is asked of
 * the transaction before it is published here ({@code AbstractEmvBehavior#goToStep}).
 *
 * <p>{@code PosTerminal} owns the transaction itself — its thread, busy, cancel, its steps and
 * its end — and runs the vendor's behavior once per transaction, handing it the transaction. The
 * behavior keeps this engine (given once, {@code AbstractEmvBehavior#attach}); {@code PosTerminal}
 * reaches it through {@code AbstractEmvBehavior#engine()}.
 *
 * <p>The one way back out is {@link #authorize}: a behavior goes online from inside the kernel,
 * so the engine hands that request to the {@link Authorizer} {@code PosTerminal} gave it —
 * {@code PosTerminal#authorize}, which holds the communication port. The engine never holds the
 * port itself.
 */
public final class EmvEngine {

    /**
     * Not serialized: a step method publishes the next step from inside delivery, and it has to
     * run right there, not after the current one returns.
     */
    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    private final Authorizer authorizer;
    private final ApduTrace apduTrace = new ApduTrace();

    /** How the engine goes online for its behavior — {@code PosTerminal#authorize}. */
    public interface Authorizer {
        AuthResult authorize(TransactionConfig config);
    }

    public EmvEngine(Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    public Observable<EmvStepEvent> emvSteps() {
        return emvSteps.hide();
    }

    /** A new transaction: a fresh trace. */
    public void begin() {
        apduTrace.beginTransaction();
    }

    /**
     * This transaction's APDU trace — started fresh by {@link #begin()}, fed by this engine's
     * step notifications and by the vendor device layer (which the vendor behavior hands this
     * instance at transaction start), read by the UI through {@code PosTerminal#apduTrace()}.
     */
    public ApduTrace apduTrace() {
        return apduTrace;
    }

    /**
     * Publishes {@code step}: every subscriber — the screen, and the behavior's step methods —
     * gets it before this returns. Its caller has already asked the transaction whether a step
     * may still run.
     */
    public void notifyEmvStep(EmvStep step, @Nullable String detail) {
        EmvStepEvent event = new EmvStepEvent(step, detail);
        EmvLog.d("EmvStep: " + event);
        apduTrace.kernelStep(event.toString());
        emvSteps.onNext(event);
    }

    public void notifyEmvStep(EmvStep step) {
        notifyEmvStep(step, null);
    }

    /**
     * Goes online for the behavior ({@code AbstractEmvBehavior#authorizeOnline}) through the
     * {@link Authorizer} {@code PosTerminal} gave this engine — the host call and its
     * {@code ONLINE_*} steps live there.
     */
    public AuthResult authorize(TransactionConfig config) {
        return authorizer.authorize(config);
    }
}
