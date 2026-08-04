package com.emvenhance.vendor;

import androidx.annotation.Nullable;
import com.emvenhance.core.CardPresence;
import com.emvenhance.core.CardSearchListener;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.PosTerminal;
import com.emvenhance.core.TransactionConfig;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ingenico POS terminal stub — demonstrates the extension pattern.
 *
 * <p>Replace the stub card search with native Tetra/Axium SDK reader APIs when the
 * Ingenico libraries are attached. Pair with {@link IngenicoEmvBehavior} for EMV.
 */
public class IngenicoTerminal extends PosTerminal {

    private static final long SEARCH_DELAY_MS = 200L;
    private final AtomicBoolean stopSearch = new AtomicBoolean(false);

    public IngenicoTerminal() {
        super(new EmvEngine(), new IngenicoEmvBehavior());
    }

    @Nullable
    @Override
    public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
        stopSearch.set(false);
        listener.onSearchStarted(config);

        // TODO: open Ingenico ICC/PICC/Mag readers with native SDK and poll.
        try {
            Thread.sleep(SEARCH_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.onSearchCancelled();
            return null;
        }

        if (stopSearch.get() || isSearchCancelled()) {
            listener.onSearchCancelled();
            return null;
        }

        // Stub detection so the architecture path is exercisable without the SDK.
        if (config.isManual()) {
            CardPresence card = CardPresence.manual("4111111111111111");
            listener.onManualEntrySelected(card);
            return card;
        }
        if (config.isMagstripe()) {
            CardPresence card = CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null);
            listener.onMagstripeDetected(card);
            return card;
        }
        if (config.isContactless()) {
            CardPresence card = CardPresence.contactless(new byte[] {0x0A, 0x0B});
            listener.onContactlessDetected(card);
            return card;
        }
        if (config.allowsChip()) {
            CardPresence card = CardPresence.chip();
            listener.onChipDetected(card);
            return card;
        }
        if (config.allowsContactless()) {
            CardPresence card = CardPresence.contactless(new byte[] {0x0A, 0x0B});
            listener.onContactlessDetected(card);
            return card;
        }
        if (config.allowsMagstripe()) {
            CardPresence card = CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null);
            listener.onMagstripeDetected(card);
            return card;
        }

        listener.onSearchTimeout();
        return null;
    }

    @Override
    protected void cancelCardSearch() {
        stopSearch.set(true);
        // TODO: cancel native Ingenico reader wait
    }
}
