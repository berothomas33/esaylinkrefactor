package com.emvenhance.vendor.fake;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.emv.EmvStep;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.transaction.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.AbstractEmvBehavior;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fake vendor: each step does its work, then {@link #goToStep} publishes on the EmvStep
 * observable and runs the next vendor method.
 */
public class FakeEmvBehavior extends AbstractEmvBehavior {

    private static final long SEARCH_DELAY_MS = 200L;
    private final AtomicBoolean stopSearch = new AtomicBoolean(false);

    @Nullable
    private AuthResult lastAuth;

    @Override
    public void onTerminalInitialization(TransactionConfig config) {
        lastAuth = null;
        // Stop here — the card search (SEARCH_CARD) is the base class's next step. Do NOT goToStep.
    }

    /** A demo search: a card of the entry mode asked for, after a short wait. */
    @Override
    public void onSearchCard(TransactionConfig config, @Nullable CardPresence found) {
        CardSearchListener listener = cardSearchListener();
        stopSearch.set(false);
        listener.onSearchStarted(config);

        try {
            Thread.sleep(SEARCH_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.onSearchCancelled();
            return;
        }

        if (stopSearch.get() || !isRunning()) {
            listener.onSearchCancelled();
            return;
        }

        if (config.isManual()) {
            CardPresence card = CardPresence.manual("4111111111111111");
            listener.onManualEntrySelected(card);
            return;
        }
        if (config.isMagstripe()) {
            CardPresence card = CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null);
            listener.onMagstripeDetected(card);
            return;
        }
        if (config.isContactless()) {
            CardPresence card = CardPresence.contactless(new byte[] {0x01, 0x02});
            listener.onContactlessDetected(card);
            return;
        }
        // CONTACT and ANY → chip first (demo)
        if (config.allowsChip()) {
            CardPresence card = CardPresence.chip();
            listener.onChipDetected(card);
            return;
        }
        if (config.allowsContactless()) {
            CardPresence card = CardPresence.contactless(new byte[] {0x01, 0x02});
            listener.onContactlessDetected(card);
            return;
        }
        if (config.allowsMagstripe()) {
            CardPresence card = CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null);
            listener.onMagstripeDetected(card);
            return;
        }
        if (config.allowsManual()) {
            CardPresence card = CardPresence.manual("4111111111111111");
            listener.onManualEntrySelected(card);
            return;
        }

        listener.onSearchTimeout();
    }

    @Override
    public void cancelCardSearch() {
        stopSearch.set(true);
    }

    @Override
    public void onApplicationSelection(TransactionConfig config, CardPresence card) {
        if (card.isContactless()) {
            // Abbreviated CLSS: skip wait/final/ODA/… → read → complete offline
            goToStep(EmvStep.READ_APPLICATION_DATA, "contactless");
            return;
        }
        goToStep(EmvStep.WAIT_APPLICATION_SELECTION);
    }

    @Override
    public void onWaitApplicationSelection(TransactionConfig config, CardPresence card) {
        goToStep(EmvStep.FINAL_APPLICATION_SELECTION);
    }

    @Override
    public void onFinalApplicationSelection(TransactionConfig config, CardPresence card) {
        publish(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        goToStep(EmvStep.READ_APPLICATION_DATA);
    }

    @Override
    public void onReadApplicationData(TransactionConfig config, CardPresence card) {
        if (card.isChip()) {
            publish(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, "4111111111111111")
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "Demo Bank")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "CARD HOLDER")
                    .put(TransactionStepEvent.KEY_MODE, "Contact")
                    .build());
        } else if (card.isContactless()) {
            publish(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, "5555444433332222")
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "Demo Bank")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "CL HOLDER")
                    .put(TransactionStepEvent.KEY_MODE, "Contactless")
                    .build());
        } else if (card.isMagstripe()) {
            String pan = card.getTrack2() != null
                    ? card.getTrack2().split("[=D]")[0] : "4111111111111111";
            publish(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, pan)
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "MAG Bank")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "")
                    .put(TransactionStepEvent.KEY_MODE, "Magstripe")
                    .build());
        } else if (card.isManual()) {
            String pan = card.getManualPan() != null ? card.getManualPan() : "";
            publish(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, pan)
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "MANUAL")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "")
                    .put(TransactionStepEvent.KEY_MODE, "Manual")
                    .build());
        }
        sleep(200);

        if (card.isContactless()) {
            goToStep(EmvStep.TRANSACTION_COMPLETION);
            return;
        }
        if (isSynchronousEntry(card)) {
            goToStep(EmvStep.START_ONLINE_PROCESS);
            return;
        }
        goToStep(EmvStep.SET_TRANSACTION_DATA);
    }

    @Override
    public void onSetTransactionData(TransactionConfig config, CardPresence card) {
        goToStep(EmvStep.OFFLINE_DATA_AUTHENTICATION);
    }

    @Override
    public void onOfflineDataAuthentication(TransactionConfig config, CardPresence card) {
        goToStep(EmvStep.PROCESS_RESTRICTIONS);
    }

    @Override
    public void onProcessRestrictions(TransactionConfig config, CardPresence card) {
        goToStep(EmvStep.CARDHOLDER_VERIFICATION);
    }

    @Override
    public void onCardholderVerification(TransactionConfig config, CardPresence card) {
        goToStep(EmvStep.OFFLINE_PIN_VERIFICATION);
    }

    @Override
    public void onOfflinePinVerification(TransactionConfig config, CardPresence card) {
        publish(TransactionStepEvent.builder(TransactionStep.CARDHOLDER_VERIFIED)
                .put(TransactionStepEvent.KEY_ONLINE_PIN, true)
                .put(TransactionStepEvent.KEY_PIN_BYPASS, true)
                .put(TransactionStepEvent.KEY_PIN_TRIES_LEFT, 3)
                .build());
        goToStep(EmvStep.TERMINAL_RISK_MANAGEMENT);
    }

    @Override
    public void onTerminalRiskManagement(TransactionConfig config, CardPresence card) {
        goToStep(EmvStep.TERMINAL_ACTION_ANALYSIS);
    }

    @Override
    public void onTerminalActionAnalysis(TransactionConfig config, CardPresence card) {
        goToStep(EmvStep.START_ONLINE_PROCESS);
    }

    @Override
    public void onStartOnlineProcess(TransactionConfig config, CardPresence card) {
        lastAuth = authorizeOnline(); // a cancelled transaction never runs this step
        if (card.isChip()) {
            goToStep(EmvStep.ISSUER_AUTHENTICATION);
        } else {
            goToStep(EmvStep.TRANSACTION_COMPLETION);
        }
    }

    /**
     * Demo card data (no kernel): a fixed ARQC (9F26), CID 80 (9F27), ATC (9F36) and IAD (9F10),
     * so the online request has the same shape as a real card's.
     */
    @Override
    public byte[] readIccTag(int tag) {
        switch (tag) {
            case 0x9F26:
                return new byte[] {(byte) 0xF4, 0x3D, 0x16, 0x6E, (byte) 0xC3, 0x61, (byte) 0xA9, (byte) 0xB2};
            case 0x9F27:
                return new byte[] {(byte) 0x80};
            case 0x9F36:
                return new byte[] {0x00, 0x01};
            case 0x9F10:
                return new byte[] {0x01, 0x10, (byte) 0xA5, 0x00, 0x0F, 0x04, 0x00, 0x00};
            default:
                return new byte[0];
        }
    }

    @Override
    public void onIssuerAuthentication(TransactionConfig config, CardPresence card) {
        goToStep(EmvStep.SCRIPT_PROCESSING);
    }

    @Override
    public void onScriptProcessing(TransactionConfig config, CardPresence card) {
        goToStep(EmvStep.TRANSACTION_COMPLETION);
    }

    @Override
    public void onTransactionCompletion(TransactionConfig config, CardPresence card) {
        if (card.isContactless() && lastAuth == null) {
            finishApproved("OFFLINE APPROVED");
            return;
        }
        if (lastAuth != null && lastAuth.isApproved()) {
            finishApproved("ONLINE APPROVED");
        } else if (lastAuth != null) {
            finishDeclined(lastAuth.getMessage() != null ? lastAuth.getMessage() : "DECLINED");
        } else {
            finishApproved("APPROVED");
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
