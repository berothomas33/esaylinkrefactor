package com.emvenhance.vendor;

import androidx.annotation.Nullable;
import com.emvenhance.core.CardPresence;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.PosTerminal;
import com.emvenhance.core.TransactionConfig;
import com.emvenhance.emvflow.EmvFlowRuntime;
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
import com.pax.emvservice.export.EmvServiceConstant;
import com.pax.emvservice.export.IMagCardService;
import com.sankuai.waimai.router.Router;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PAX POS terminal — hardware only (readers, card search, cancel).
 *
 * <pre>
 *   PaxTerminal (this)
 *     └── PaxEmvBehavior  ← full EMV lifecycle + PAX SDK
 * </pre>
 */
public class PaxTerminal extends PosTerminal {

    private static final String TAG = "PaxTerminal";
    private static final long SEARCH_TIMEOUT_MS = 60_000L;
    private static final long POLL_INTERVAL_MS = 50L;

    private final AtomicBoolean stopSearch = new AtomicBoolean(false);

    public PaxTerminal() {
        super(new EmvEngine(),
                new PaxEmvBehavior(new PaxCommunicationBehavior(), new PaxPrinterBehavior()));
    }

    @Override
    protected void initializeVendor() {
        LogUtils.i(TAG, "PAX terminal initialized");
    }

    @Nullable
    @Override
    public CardPresence searchCard(TransactionConfig config) {
        stopSearch.set(false);
        IDAL dal = EmvFlowRuntime.getDal();
        if (dal == null) {
            LogUtils.e(TAG, "DAL not ready");
            return null;
        }

        byte mode = toSearchMode(config);
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

            long deadline = System.currentTimeMillis() + SEARCH_TIMEOUT_MS;
            while (!stopSearch.get() && !isSearchCancelled()
                    && System.currentTimeMillis() < deadline) {
                CardPresence found = pollOnce(mag, icc, picc, mode);
                if (found != null) {
                    return found;
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            LogUtils.e(TAG, "searchCard failed", e);
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
            byte mode) {
        try {
            if (SearchMode.isSupportMag(mode) && mag != null && mag.isSwiped()) {
                String t1 = null;
                String t2 = null;
                String t3 = null;
                IMagCardService magService = Router.getService(
                        IMagCardService.class, EmvServiceConstant.EMVSERVICE_MAG_CARD);
                if (magService != null) {
                    magService.magRead();
                    t1 = magService.getTrack1();
                    t2 = magService.getTrack2();
                    t3 = magService.getTrack3();
                } else {
                    TrackData data = mag.read();
                    if (data != null) {
                        t1 = data.getTrack1();
                        t2 = data.getTrack2();
                        t3 = data.getTrack3();
                    }
                }
                if (t2 != null && !t2.isEmpty()) {
                    closeQuietly(null, icc, picc);
                    return CardPresence.magstripe(t1, t2, t3);
                }
            }
            if (SearchMode.isSupportIcc(mode) && icc != null && icc.detect((byte) 0)) {
                byte[] atr = icc.init((byte) 0);
                if (atr != null) {
                    closeQuietly(mag, null, picc);
                    return CardPresence.contact();
                }
            }
            if (SearchMode.isSupportInternalPicc(mode) && picc != null) {
                PiccCardInfo info = picc.detect(EDetectMode.EMV_AB);
                if (info != null) {
                    closeQuietly(mag, icc, null);
                    return CardPresence.contactless(info.getSerialInfo());
                }
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "pollOnce", e);
        }
        return null;
    }

    private static byte toSearchMode(TransactionConfig config) {
        byte mode = 0;
        if (config.isContact()) {
            mode |= SearchMode.INSERT;
        }
        if (config.isContactless()) {
            mode |= SearchMode.INTERNAL_WAVE;
        }
        if (config.isMagstripe()) {
            mode |= SearchMode.SWIPE;
        }
        return mode;
    }

    private static void closeQuietly(@Nullable IMag mag, @Nullable IIcc icc, @Nullable IPicc picc) {
        try {
            if (mag != null) {
                mag.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (icc != null) {
                icc.close((byte) 0);
            }
        } catch (Exception ignored) {
        }
        try {
            if (picc != null) {
                picc.close();
            }
        } catch (Exception ignored) {
        }
    }
}
