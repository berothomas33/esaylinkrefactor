package com.emvenhance.vendor;

import androidx.annotation.Nullable;
import com.emvenhance.core.CardPresence;
import com.emvenhance.core.PosTerminal;
import com.emvenhance.core.TransactionConfig;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fake POS terminal for off-device runs — owns simulated card search and creates
 * {@link FakeEmvBehavior} / {@link FakeEmvEngine}.
 */
public class FakeTerminal extends PosTerminal {

    private static final long SEARCH_DELAY_MS = 200L;

    private final AtomicBoolean stopSearch = new AtomicBoolean(false);

    public FakeTerminal() {
        super(new FakeEmvEngine(), new FakeEmvBehavior(),
                new FakeCommunicationBehavior(), new FakePrinterBehavior());
    }

    @Nullable
    @Override
    protected CardPresence searchCard(TransactionConfig config) {
        stopSearch.set(false);
        try {
            Thread.sleep(SEARCH_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        if (stopSearch.get() || isSearchCancelled()) {
            return null;
        }
        if (config.isMagstripe()) {
            return CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null);
        }
        if (config.isContactless()) {
            return CardPresence.contactless(new byte[] {0x01, 0x02});
        }
        return CardPresence.contact();
    }

    @Override
    protected void cancelCardSearch() {
        stopSearch.set(true);
    }
}
