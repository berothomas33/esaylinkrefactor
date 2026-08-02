package com.emvenhance.vendor;

import com.emvenhance.core.EmvBehavior;
import com.emvenhance.core.EmvStep;

/**
 * Simulated kernel so the flow and the UI can be exercised off a real terminal.
 */
public class FakeEmvBehavior implements EmvBehavior {

    @Override
    public boolean prepare(String procCode, long amountMinor, boolean contact,
            boolean contactless) {
        return true;
    }

    @Override
    public void startContact(Callback callback) {
        new Thread(() -> {
            callback.onStep(EmvStep.APPLICATION_SELECTION, null);
            sleep(200);
            callback.onStep(EmvStep.READ_APPLICATION_DATA, null);
            callback.onConfirmCard("4111111111111111", "Demo Bank", "CARD HOLDER");
            sleep(300);
            callback.onStep(EmvStep.SET_TRANSACTION_DATA, null);
            callback.onStep(EmvStep.OFFLINE_DATA_AUTHENTICATION, "DDA");
            callback.onStep(EmvStep.PROCESS_RESTRICTIONS, null);
            callback.onAskPin(true, true, 3);
            sleep(200);
            callback.onStep(EmvStep.TERMINAL_RISK_MANAGEMENT, null);
            callback.onStep(EmvStep.TERMINAL_ACTION_ANALYSIS, null);
            callback.onNeedOnline();
            callback.onStep(EmvStep.SCRIPT_PROCESSING, null);
            callback.onApproved("ONLINE APPROVED");
        }, "fake-emv-contact").start();
    }

    @Override
    public void startContactless(Callback callback) {
        new Thread(() -> {
            callback.onStep(EmvStep.APPLICATION_SELECTION, "contactless");
            sleep(200);
            callback.onStep(EmvStep.READ_APPLICATION_DATA, null);
            callback.onConfirmCard("5555444433332222", "Demo Bank", "CL HOLDER");
            callback.onRemoveCard();
            sleep(200);
            callback.onApproved("OFFLINE APPROVED");
        }, "fake-emv-contactless").start();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
