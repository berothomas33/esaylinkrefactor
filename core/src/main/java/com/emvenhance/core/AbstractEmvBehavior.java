package com.emvenhance.core;

import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * Shared defaults for vendor {@link EmvBehavior} implementations: online authorize and
 * receipt printing. SDK-specific flow stays in Pax/Ingenico/… subclasses.
 */
public abstract class AbstractEmvBehavior implements EmvBehavior {

    protected final CommunicationBehavior communication;
    protected final PrinterBehavior printer;

    @Nullable
    protected EmvEngine engine;
    @Nullable
    protected TransactionConfig activeConfig;

    protected AbstractEmvBehavior(CommunicationBehavior communication, PrinterBehavior printer) {
        this.communication = communication;
        this.printer = printer;
    }

    @Override
    public void onOnlineRequired(TransactionStepEvent event) {
        EmvEngine eng = engine;
        TransactionConfig config = activeConfig;
        if (eng == null || config == null) {
            if (eng != null) {
                eng.notifyError("Online required but behavior is not bound to a transaction");
            }
            return;
        }

        eng.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));
        try {
            AuthResult authResult = communication.authorize(config).blockingGet();
            eng.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.ONLINE_COMPLETED)
                    .put(TransactionStepEvent.KEY_RESULT, authResult.getMessage())
                    .build());
            deliverOnlineResult(authResult);
        } catch (Exception e) {
            AuthResult declined = AuthResult.declined("96",
                    e.getMessage() != null ? e.getMessage() : "Online authorization failed");
            deliverOnlineResult(declined);
        }
    }

    @Override
    public void onApproved(TransactionStepEvent event) {
        onPrintReceipt(event);
    }

    @Override
    public void onPrintReceipt(TransactionStepEvent event) {
        List<String> lines = buildReceipt(event);
        try {
            printer.print(lines).blockingAwait();
        } catch (Exception ignored) {
            // Print failure is non-fatal.
        }
    }

    /** Unblocks a vendor kernel waiting in {@code startOnlineProcess}. */
    protected abstract void deliverOnlineResult(AuthResult authResult);

    protected List<String> buildReceipt(TransactionStepEvent event) {
        return Arrays.asList(
                "=== RECEIPT ===",
                "PAN:    " + event.getString(TransactionStepEvent.KEY_PAN),
                "Issuer: " + event.getString(TransactionStepEvent.KEY_ISSUER_NAME),
                "Amount: " + (activeConfig != null ? activeConfig.getAmountMinor() : "—"),
                "Result: " + event.getString(TransactionStepEvent.KEY_RESULT),
                "==============="
        );
    }
}
