package com.emvenhance.vendor;

import androidx.annotation.Nullable;
import com.emvenhance.core.CardPresence;
import com.emvenhance.core.CardSearchListener;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.PosTerminal;
import com.emvenhance.core.TransactionConfig;
import com.pax.bizentity.entity.SearchMode;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IDAL;
import com.pax.dal.IIcc;
import com.pax.dal.IMag;
import com.pax.dal.IPicc;
import com.pax.dal.entity.EDetectMode;
import com.pax.dal.entity.EPiccType;
import com.pax.dal.entity.PiccCardInfo;
import com.pax.dal.entity.TrackData;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PAX POS terminal — owns Neptune DAL card search and creates {@link PaxEmvBehavior}.
 *
 * <p>To add PAX support to a project, instantiate this class. It creates its own
 * {@link EmvEngine} and {@link PaxEmvBehavior} internally — no other wiring needed.
 */
public class PaxTerminal extends PosTerminal {

    private static final String TAG = "PaxTerminal";
    private static final long SEARCH_TIMEOUT_MS = 60_000L;
    private static final long POLL_INTERVAL_MS = 50L;

    private final PaxKernel kernel;
    private final AtomicBoolean stopSearch = new AtomicBoolean(false);

    public PaxTerminal() {
        this(new PaxKernel());
    }

    private PaxTerminal(PaxKernel kernel) {
        super(new EmvEngine(), new PaxEmvBehavior(kernel));
        this.kernel = kernel;
    }

    @Override
    protected void initializeVendor() {
        LogUtils.i(TAG, "PAX terminal initialized");
    }

    @Nullable
    @Override
    public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
        stopSearch.set(false);

        if (config.isManual()) {
            listener.onSearchStarted(config);
            CardPresence card = CardPresence.manual(null);
            listener.onManualEntrySelected(card);
            return card;
        }

        IDAL dal = PaxRuntime.getDal();
        if (dal == null) {
            LogUtils.e(TAG, "DAL not ready");
            listener.onReaderError("DAL not ready");
            return null;
        }

        byte mode = toSearchMode(config);
        if (mode == 0) {
            listener.onReaderError("No searchable entry mode enabled");
            return null;
        }

        IMag mag = null;
        IIcc icc = null;
        IPicc picc = null;

        try {
            if (SearchMode.isSupportMag(mode)) {
                mag = dal.getMag();
                mag.close();
                mag.open();
                mag.reset();
            }
            if (SearchMode.isSupportIcc(mode)) {
                icc = dal.getIcc();
                icc.close((byte) 0);
            }
            if (SearchMode.isSupportInternalPicc(mode)) {
                picc = dal.getPicc(EPiccType.INTERNAL);
                picc.close();
                picc.open();
            }

            listener.onSearchStarted(config);

            long deadline = System.currentTimeMillis() + SEARCH_TIMEOUT_MS;
            while (!stopSearch.get() && !isSearchCancelled()
                    && System.currentTimeMillis() < deadline) {
                CardPresence found = pollOnce(mag, icc, picc, mode, listener);
                if (found != null) {
                    return found;
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    listener.onSearchCancelled();
                    return null;
                }
            }

            if (stopSearch.get() || isSearchCancelled()) {
                listener.onSearchCancelled();
            } else {
                listener.onSearchTimeout();
            }
            return null;
        } catch (Exception e) {
            LogUtils.e(TAG, "searchCard failed", e);
            listener.onReaderError(e.getMessage() != null ? e.getMessage() : "Reader error");
            return null;
        } finally {
            if (stopSearch.get() || isSearchCancelled()) {
                closeQuietly(mag, icc, picc);
            }
        }
    }

    @Override
    protected void cancelCardSearch() {
        stopSearch.set(true);
    }

    @Nullable
    private CardPresence pollOnce(@Nullable IMag mag, @Nullable IIcc icc, @Nullable IPicc picc,
            byte mode, CardSearchListener listener) {
        try {
            if (SearchMode.isSupportMag(mode) && mag != null && mag.isSwiped()) {
                String t1;
                String t2;
                String t3;
                kernel.mag.magRead();
                t1 = kernel.mag.getTrack1();
                t2 = kernel.mag.getTrack2();
                t3 = kernel.mag.getTrack3();
                if (t2 == null || t2.isEmpty()) {
                    TrackData data = mag.read();
                    if (data != null) {
                        t1 = data.getTrack1();
                        t2 = data.getTrack2();
                        t3 = data.getTrack3();
                    }
                }
                if (t2 != null && !t2.isEmpty()) {
                    closeQuietly(null, icc, picc);
                    CardPresence card = CardPresence.magstripe(t1, t2, t3);
                    listener.onMagstripeDetected(card);
                    return card;
                }
            }
            if (SearchMode.isSupportIcc(mode) && icc != null && icc.detect((byte) 0)) {
                byte[] atr = icc.init((byte) 0);
                if (atr != null) {
                    closeQuietly(mag, null, picc);
                    CardPresence card = CardPresence.chip();
                    listener.onChipDetected(card);
                    return card;
                }
            }
            if (SearchMode.isSupportInternalPicc(mode) && picc != null) {
                PiccCardInfo info = picc.detect(EDetectMode.EMV_AB);
                if (info != null) {
                    closeQuietly(mag, icc, null);
                    CardPresence card = CardPresence.contactless(info.getSerialInfo());
                    listener.onContactlessDetected(card);
                    return card;
                }
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "pollOnce", e);
            listener.onReaderError(e.getMessage() != null ? e.getMessage() : "poll failed");
        }
        return null;
    }

    private static byte toSearchMode(TransactionConfig config) {
        byte mode = 0;
        if (config.allowsChip()) mode |= SearchMode.INSERT;
        if (config.allowsContactless()) mode |= SearchMode.INTERNAL_WAVE;
        if (config.allowsMagstripe()) mode |= SearchMode.SWIPE;
        return mode;
    }

    private static void closeQuietly(@Nullable IMag mag, @Nullable IIcc icc, @Nullable IPicc picc) {
        try { if (mag != null) mag.close(); } catch (Exception ignored) { }
        try { if (icc != null) icc.close((byte) 0); } catch (Exception ignored) { }
        try { if (picc != null) picc.close(); } catch (Exception ignored) { }
    }
}
