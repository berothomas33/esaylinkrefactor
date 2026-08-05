package com.emvenhance.vendor.ingenico;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.terminal.EmvBehavior;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ingenico EMV stub. Uses the terminal-owned {@link CommunicationBehavior} for online.
 */
public class IngenicoEmvBehavior implements EmvBehavior {

    private static final String TAG = "IngenicoEmvBehavior";

    private final CommunicationBehavior communication;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Nullable
    private TransactionConfig activeConfig;

    public IngenicoEmvBehavior(CommunicationBehavior communication) {
        this.communication = communication;
    }

    @Override
    public boolean prepare(@NonNull EmvEngine engine, @NonNull TransactionConfig config) {
        this.activeConfig = config;
        cancelled.set(false);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        engine.notifyEmvStep(EmvStep.TERMINAL_INITIALIZATION, "Ingenico stub");
        Log.w(TAG, "Ingenico EMV SDK not attached — stub prepare");
        return true;
    }

    @Override
    public void start(@NonNull EmvEngine engine, @NonNull TransactionConfig config,
            @NonNull CardPresence card) {
        this.activeConfig = config;
        cancelled.set(false);

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
    }

    private void stubFlow(EmvEngine engine, String mode, String pan) {
        engine.notifyEmvStep(EmvStep.APPLICATION_SELECTION, "ingenico-" + mode);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        engine.notifyEmvStep(EmvStep.READ_APPLICATION_DATA);
        engine.notifyCardDetected(pan, "Ingenico Stub", "", mode);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));

        engine.notifyEmvStep(EmvStep.START_ONLINE_PROCESS);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));

        AuthResult auth = authorize();
        engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.ONLINE_COMPLETED)
                .put(TransactionStepEvent.KEY_RESULT, auth.getMessage())
                .build());
        engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION);
        if (auth.isApproved()) {
            engine.notifyApproved("INGENICO STUB APPROVED");
        } else {
            engine.notifyDeclined(auth.getMessage());
        }
        engine.notifyCompleted();
    }

    private AuthResult authorize() {
        if (cancelled.get()) {
            return AuthResult.declined("17", "Cancelled");
        }
        try {
            return communication.authorize(activeConfig).blockingGet();
        } catch (Exception e) {
            return AuthResult.declined("96",
                    e.getMessage() != null ? e.getMessage() : "Online failed");
        }
    }
}
