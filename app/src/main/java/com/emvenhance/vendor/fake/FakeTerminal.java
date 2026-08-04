package com.emvenhance.vendor.fake;

import androidx.annotation.Nullable;
import com.emvenhance.core.CardPresence;
import com.emvenhance.core.CardSearchListener;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.PosTerminal;
import com.emvenhance.core.TransactionConfig;
import com.emvenhance.core.host.HostDefaults;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demo terminal — simulates search events so the UI works without POS hardware.
 */
public class FakeTerminal extends PosTerminal {

    private static final long SEARCH_DELAY_MS = 200L;
    private final AtomicBoolean stopSearch = new AtomicBoolean(false);

    public FakeTerminal() {
        super(new EmvEngine(),
                new FakeEmvBehavior(HostDefaults.approveAlways(), HostDefaults.logPrinter("FakePrinter")));
    }

    @Nullable
    @Override
    public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
        stopSearch.set(false);
        listener.onSearchStarted(config);

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
            CardPresence card = CardPresence.contactless(new byte[] {0x01, 0x02});
            listener.onContactlessDetected(card);
            return card;
        }
        // CONTACT and ANY → chip first (demo)
        if (config.allowsChip()) {
            CardPresence card = CardPresence.chip();
            listener.onChipDetected(card);
            return card;
        }
        if (config.allowsContactless()) {
            CardPresence card = CardPresence.contactless(new byte[] {0x01, 0x02});
            listener.onContactlessDetected(card);
            return card;
        }
        if (config.allowsMagstripe()) {
            CardPresence card = CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null);
            listener.onMagstripeDetected(card);
            return card;
        }
        if (config.allowsManual()) {
            CardPresence card = CardPresence.manual("4111111111111111");
            listener.onManualEntrySelected(card);
            return card;
        }

        listener.onSearchTimeout();
        return null;
    }

    @Override
    protected void cancelCardSearch() {
        stopSearch.set(true);
    }
}
