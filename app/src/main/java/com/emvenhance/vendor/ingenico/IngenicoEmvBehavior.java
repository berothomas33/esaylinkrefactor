package com.emvenhance.vendor.ingenico;

import androidx.annotation.NonNull;
import com.emvenhance.core.AbstractEmvBehavior;
import com.emvenhance.core.AuthResult;
import com.emvenhance.core.CardPresence;
import com.emvenhance.core.CommunicationBehavior;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.EmvStep;
import com.emvenhance.core.PrinterBehavior;
import com.emvenhance.core.TransactionConfig;
import com.emvenhance.core.TransactionStep;
import com.emvenhance.core.TransactionStepEvent;
import android.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ingenico vendor EMV behavior stub — runs after {@link IngenicoTerminal#searchCard}
 * selects an entry method.
 *
 * <p>Replace the stub flows with Tetra/Axium SDK calls when the Ingenico libraries are
 * attached. Online authorize / print remain in {@link AbstractEmvBehavior}.
 */
public class IngenicoEmvBehavior extends AbstractEmvBehavior {

    private static final String TAG = "IngenicoEmvBehavior";

    private final AtomicReference<AuthResult> pendingAuth = new AtomicReference<>();
    private volatile CountDownLatch authLatch;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public IngenicoEmvBehavior(CommunicationBehavior communication, PrinterBehavior printer) {
        super(communication, printer);
    }

    @Override
    public boolean prepare(@NonNull EmvEngine engine, @NonNull TransactionConfig config) {
        this.engine = engine;
        this.activeConfig = config;
        cancelled.set(false);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        engine.notifyEmvStep(EmvStep.TERMINAL_INITIALIZATION, "Ingenico stub");
        Log.w(TAG, "Ingenico EMV SDK not attached — stub prepare");
        // TODO: load Ingenico AID/CAPK / terminal params from Tetra/Axium SDK
        return true;
    }

    @Override
    public void start(@NonNull EmvEngine engine, @NonNull TransactionConfig config,
            @NonNull CardPresence card) {
        this.engine = engine;
        this.activeConfig = config;
        cancelled.set(false);

        // TODO: dispatch to native Ingenico chip / contactless / mag / manual kernels
        switch (card.getEntryMethod()) {
            case CHIP:
                stubFlow(engine, "Chip", "4111111111111111");
                break;
            case CONTACTLESS:
                stubFlow(engine, "Contactless", "5555444433332222");
                break;
            case MAGSTRIPE:
                String magPan = card.getTrack2() != null
                        ? card.getTrack2().split("[=D]")[0] : "4111111111111111";
                stubFlow(engine, "Magstripe", magPan);
                break;
            case MANUAL:
                stubFlow(engine, "Manual",
                        card.getManualPan() != null ? card.getManualPan() : "");
                break;
            default:
                engine.notifyError("Unsupported entry method: " + card.getEntryMethod());
                break;
        }
    }

    @Override
    public void cancel() {
        cancelled.set(true);
        CountDownLatch latch = authLatch;
        if (latch != null) {
            pendingAuth.compareAndSet(null, AuthResult.declined("17", "Cancelled"));
            latch.countDown();
        }
        // TODO: abort native Ingenico EMV process
    }

    @Override
    protected void deliverOnlineResult(AuthResult authResult) {
        pendingAuth.set(authResult);
        CountDownLatch latch = authLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    private void stubFlow(EmvEngine engine, String mode, String pan) {
        engine.notifyEmvStep(EmvStep.APPLICATION_SELECTION, "ingenico-" + mode);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        engine.notifyEmvStep(EmvStep.READ_APPLICATION_DATA);
        engine.notifyCardDetected(pan, "Ingenico Stub", "", mode);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));

        engine.notifyEmvStep(EmvStep.START_ONLINE_PROCESS);
        pendingAuth.set(null);
        authLatch = new CountDownLatch(1);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));

        AuthResult auth;
        try {
            authLatch.await();
            auth = pendingAuth.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            auth = null;
        } finally {
            authLatch = null;
        }

        engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION);
        if (auth != null && auth.isApproved()) {
            engine.notifyApproved("INGENICO STUB APPROVED");
        } else {
            engine.notifyDeclined(auth != null ? auth.getMessage() : "Ingenico stub declined");
        }
        engine.notifyCompleted();
    }
}
