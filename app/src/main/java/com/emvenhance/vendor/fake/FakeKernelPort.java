package com.emvenhance.vendor.fake;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.port.EmvKernelPort;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simulated L2 kernel for the Fake vendor. Populates {@link EmvContext} with demo card data.
 */
public final class FakeKernelPort implements EmvKernelPort {

    private final AtomicBoolean aborted = new AtomicBoolean(false);

    @Override
    public boolean preTrans(EmvContext context) {
        return !aborted.get();
    }

    @Override
    public List<String> buildCandidates(EmvContext context) {
        if (aborted.get()) {
            return Collections.emptyList();
        }
        EntryMethod entry = context.getEntryMethod();
        if (entry == EntryMethod.CONTACTLESS) {
            return Collections.singletonList("A0000000041010");
        }
        // Contact demo: two AIDs so WAIT_APPLICATION_SELECTION can run (auto-picks first)
        return Arrays.asList("A0000000031010", "A0000000041010");
    }

    @Override
    public boolean finalSelect(EmvContext context, String aid) {
        context.put(EmvContext.KEY_SELECTED_AID, aid);
        return !aborted.get();
    }

    @Override
    public boolean readApplicationData(EmvContext context) {
        if (aborted.get()) {
            return false;
        }
        CardPresence card = context.getCard();
        if (card == null) {
            return false;
        }
        switch (card.getEntryMethod()) {
            case CHIP:
                context.put(EmvContext.KEY_PAN, "4111111111111111");
                context.put(EmvContext.KEY_ISSUER, "Demo Bank");
                context.put(EmvContext.KEY_CARDHOLDER, "CARD HOLDER");
                sleep(200);
                break;
            case CONTACTLESS:
                context.put(EmvContext.KEY_PAN, "5555444433332222");
                context.put(EmvContext.KEY_ISSUER, "Demo Bank");
                context.put(EmvContext.KEY_CARDHOLDER, "CL HOLDER");
                sleep(200);
                break;
            case MAGSTRIPE:
                String track2 = card.getTrack2();
                String pan = track2 != null ? track2.split("[=D]")[0] : "4111111111111111";
                context.put(EmvContext.KEY_PAN, pan);
                context.put(EmvContext.KEY_ISSUER, "MAG Bank");
                context.put(EmvContext.KEY_CARDHOLDER, "");
                break;
            case MANUAL:
                context.put(EmvContext.KEY_PAN,
                        card.getManualPan() != null ? card.getManualPan() : "");
                context.put(EmvContext.KEY_ISSUER, "MANUAL");
                context.put(EmvContext.KEY_CARDHOLDER, "");
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean setTransactionData(EmvContext context) {
        return !aborted.get();
    }

    @Nullable
    @Override
    public String offlineDataAuthentication(EmvContext context) {
        return aborted.get() ? null : "DDA";
    }

    @Override
    public boolean processRestrictions(EmvContext context) {
        return !aborted.get();
    }

    @Override
    public String processCvm(EmvContext context) {
        return aborted.get() ? null : "OFFLINE_PIN";
    }

    @Override
    public boolean verifyOfflinePin(EmvContext context) {
        context.put(EmvContext.KEY_PIN_ONLINE, true);
        context.put(EmvContext.KEY_PIN_BYPASS, true);
        context.put(EmvContext.KEY_PIN_TRIES, 3);
        return !aborted.get();
    }

    @Override
    public boolean terminalRiskManagement(EmvContext context) {
        return !aborted.get();
    }

    @Override
    public String terminalActionAnalysis(EmvContext context) {
        if (aborted.get()) {
            return null;
        }
        // Contact Fake path always goes online (matches previous FakeEmvBehavior)
        return "ONLINE";
    }

    @Override
    public boolean startOnlineProcess(EmvContext context) {
        return !aborted.get();
    }

    @Override
    public boolean issuerAuthentication(EmvContext context) {
        return !aborted.get();
    }

    @Override
    public boolean processScripts(EmvContext context) {
        return !aborted.get();
    }

    @Override
    public boolean completeTransaction(EmvContext context) {
        return !aborted.get();
    }

    @Override
    public void abort() {
        aborted.set(true);
    }

    /** Reset abort flag for a new transaction. */
    public void reset() {
        aborted.set(false);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
