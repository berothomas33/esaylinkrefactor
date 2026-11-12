package com.emvenhance.vendor.ingenico;

import android.util.Log;
import androidx.annotation.Nullable;
import com.emvenhance.core.emv.Field55;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.emv.EmvStep;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.transaction.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.AbstractEmvBehavior;
import java.util.concurrent.atomic.AtomicBoolean;

public class IngenicoEmvBehavior extends AbstractEmvBehavior {

    private static final long SEARCH_DELAY_MS = 200L;
    private final AtomicBoolean stopSearch = new AtomicBoolean(false);

    private static final String TAG = "IngenicoEmvBehavior";

    @Nullable
    private String stubPan;
    @Nullable
    private String stubMode;

    @Override
    public void onTerminalInitialization(TransactionConfig config) {
        Log.w(TAG, "Ingenico EMV SDK not attached — stub init");
        searchCard();
    }

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
            CardPresence card = CardPresence.contactless(new byte[] {0x0A, 0x0B});
            listener.onContactlessDetected(card);
            return;
        }
        if (config.allowsChip()) {
            CardPresence card = CardPresence.chip();
            listener.onChipDetected(card);
            return;
        }
        if (config.allowsContactless()) {
            CardPresence card = CardPresence.contactless(new byte[] {0x0A, 0x0B});
            listener.onContactlessDetected(card);
            return;
        }
        if (config.allowsMagstripe()) {
            CardPresence card = CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null);
            listener.onMagstripeDetected(card);
            return;
        }

        listener.onSearchTimeout();
    }

    @Override
    public void cancelCardSearch() {
        stopSearch.set(true);
    }

    @Override
    protected EmvStep firstStepAfterSearch(CardPresence card) {
        stubMode = card.getModeLabel();
        if (card.isChip()) {
            stubPan = "4111111111111111";
        } else if (card.isContactless()) {
            stubPan = "5555444433332222";
        } else if (card.isMagstripe()) {
            stubPan = card.getTrack2() != null
                    ? card.getTrack2().split("[=D]")[0] : "4111111111111111";
        } else {
            stubPan = card.getManualPan() != null ? card.getManualPan() : "";
        }
        return EmvStep.APPLICATION_SELECTION;
    }

    @Override
    public void onApplicationSelection(TransactionConfig config, CardPresence card) {
        goToStep(EmvStep.READ_APPLICATION_DATA, "ingenico-" + stubMode);
    }

    @Override
    public int onWaitApplicationSelection(TransactionConfig config, CardPresence card) {
        return -1;      // never asked: no kernel yet (fail closed if it ever is)
    }

    @Override
    public int onFinalApplicationSelection(TransactionConfig config, CardPresence card) {
        return -1;      // never asked: no kernel yet (fail closed if it ever is)
    }

    @Override
    public void onSetTransactionData(TransactionConfig config, CardPresence card) {
    }

    @Override
    public void onOfflineDataAuthentication(TransactionConfig config, CardPresence card) {
    }

    @Override
    public void onProcessRestrictions(TransactionConfig config, CardPresence card) {
    }

    @Override
    public int onCardholderVerification(TransactionConfig config, CardPresence card) {
        return -1;      // never asked: no kernel yet (fail closed if it ever is)
    }

    @Override
    public int onOfflinePinVerification(TransactionConfig config, CardPresence card) {
        return -1;      // never asked: no kernel yet (fail closed if it ever is)
    }

    @Override
    public void onTerminalRiskManagement(TransactionConfig config, CardPresence card) {
    }

    @Override
    public void onTerminalActionAnalysis(TransactionConfig config, CardPresence card) {
    }

    @Override
    public void onIssuerAuthentication(TransactionConfig config, CardPresence card) {
    }

    @Override
    public void onScriptProcessing(TransactionConfig config, CardPresence card) {
    }

    @Override
    public void onReadApplicationData(TransactionConfig config, CardPresence card) {
        publish(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        publish(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                .put(TransactionStepEvent.KEY_PAN, stubPan != null ? stubPan : "")
                .put(TransactionStepEvent.KEY_ISSUER_NAME, "Ingenico Stub")
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "")
                .put(TransactionStepEvent.KEY_MODE, stubMode != null ? stubMode : card.getModeLabel())
                .build());
        goToStep(EmvStep.ONLINE_AUTHORIZATION);
    }

    @Override
    public void onOnlineAuthorization(TransactionConfig config, CardPresence card) {
        askTheBank();
        finishFromBankAnswer();
    }

    @Override
    protected Field55 field55() {
        return Field55.read(this::readIccTag);
    }

    private byte[] readIccTag(int tag) {
        switch (tag) {
            case 0x9F26:
                return new byte[] {(byte) 0xF4, 0x3D, 0x16, 0x6E, (byte) 0xC3, 0x61, (byte) 0xA9, (byte) 0xB2};
            case 0x9F27:
                return new byte[] {(byte) 0x80};
            case 0x9F36:
                return new byte[] {0x00, 0x01};
            default:
                return new byte[0];
        }
    }

    /** The end of the flow: the outcome from the bank's answer (no step of its own). */
    private void finishFromBankAnswer() {
        AuthResult answer = bankAnswer();
        if (answer != null && answer.isApproved()) {
            finishApproved("INGENICO STUB APPROVED");
        } else {
            finishDeclined(answer != null && answer.getMessage() != null
                    ? answer.getMessage() : "DECLINED");
        }
    }
}
