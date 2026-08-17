package com.emvenhance.vendor.pax;

import android.os.Build;
import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.HostDefaults;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.pax.bizentity.entity.SearchMode;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.sp.SharedPrefUtil;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.impl.ConfigInit;
import com.pax.dal.IDAL;
import com.pax.dal.IIcc;
import com.pax.dal.IMag;
import com.pax.dal.IPicc;
import com.pax.dal.entity.EDetectMode;
import com.pax.dal.entity.EPiccType;
import com.pax.dal.entity.PiccCardInfo;
import com.pax.dal.entity.TrackData;
import com.pax.dal.exceptions.EIccDevException;
import com.pax.dal.exceptions.IccDevException;
import com.pax.poslib.model.ModelInfo;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PAX POS terminal — owns Neptune DAL card search and creates {@link PaxEmvBehavior}.
 *
 * <p>Card detection uses the native PAX SDK ({@link IIcc}/{@link IPicc}/{@link IMag}) and
 * reports every reader event through {@link CardSearchListener}. EMV after entry selection
 * is owned by {@link PaxEmvBehavior}.
 */
public class PaxTerminal extends PosTerminal {

    private static final String TAG = "PaxTerminal";
    private static final byte ICC_SLOT = 0;
    private static final long SEARCH_TIMEOUT_MS = 60_000L;
    private static final long POLL_INTERVAL_MS = 50L;
    /** VCC settle after {@code icc.close()} before the next {@code init()}. */
    private static final long ICC_POWER_SETTLE_MS = 200L;
    /** Minimum gap between failed ATR attempts so the chip is not held mute. */
    private static final long ICC_RETRY_MS = 400L;
    /**
     * How long a card may sit in the slot failing ATR before it is handed to EMV anyway.
     * Matches PAX {@code CardReaderHelper}: after this the kernel runs and EMV fallback
     * rules decide (chip unreadable → fallback to magstripe).
     */
    private static final long ICC_UNREADABLE_GRACE_MS = 3_000L;
    private static final String KEY_EMV_CONFIG_INITIALIZED = "emv_config_initialized";

    private final PaxKernel kernel;
    private final AtomicBoolean stopSearch = new AtomicBoolean(false);

    private boolean iccCardPresent;
    private boolean iccDisabled;
    private long iccFirstFailureAtMs;
    private long iccNextInitAtMs;
    private boolean iccInitWarned;

    public PaxTerminal() {
        this(new PaxKernel(),
                HostDefaults.declineUntilWired(),
                HostDefaults.logPrinter("PaxPrinter"));
    }

    private PaxTerminal(PaxKernel kernel,
            CommunicationBehavior communication,
            PrinterBehavior printer) {
        super(new EmvEngine(), new PaxEmvBehavior(kernel), communication, printer);
        this.kernel = kernel;
    }

    @Override
    protected void initializeVendor() {
        // BaseDaoHelper.getDaoSession() now opens the DB itself on first use, so no explicit
        // DaoManager.init() call is needed here.
        ensureEmvConfigInitialized();
        LogUtils.i(TAG, "PAX terminal initialized (direct kernel composition)");
    }

    /**
     * Loads AID/CAPK/scheme params into the local DB, once ever — guarded by a persisted flag
     * so a later app launch (config already on disk from a previous run) skips it instead of
     * re-parsing every JSON asset and re-inserting into Greendao again.
     */
    private void ensureEmvConfigInitialized() {
        SharedPrefUtil prefs = new SharedPrefUtil(BaseApplication.getAppContext());
        if (prefs.getBoolean(KEY_EMV_CONFIG_INITIALIZED)) {
            LogUtils.i(TAG, "EMV config already initialized, skipping");
            return;
        }
        new ConfigInit().init();
        prefs.putBoolean(KEY_EMV_CONFIG_INITIALIZED, true);
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

        IDAL dal = EmvFlowRuntime.getDal();
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
        iccDisabled = false;
        resetIccSearchState();

        try {
            PaxHardwarePermissions.logGrantState();
            if (SearchMode.isSupportMag(mode)) {
                mag = openMag(dal);
            }
            if (SearchMode.isSupportIcc(mode)) {
                icc = openIcc(dal);
                // close() at open powers the slot off; wait before the first init().
                iccNextInitAtMs = System.currentTimeMillis() + ICC_POWER_SETTLE_MS;
            }
            if (SearchMode.isSupportInternalPicc(mode)) {
                picc = openPicc(dal);
            }
            if (mag == null && icc == null && picc == null) {
                listener.onReaderError(
                        "No card reader available (need com.pax.permission.ICC / MAGCARD / PICC)");
                return null;
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
        } catch (Throwable t) {
            LogUtils.e(TAG, "searchCard failed", t);
            listener.onReaderError(t.getMessage() != null ? t.getMessage() : "Reader error");
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
        CardPresence magCard = pollMag(mag, icc, picc, mode, listener);
        if (magCard != null) {
            return magCard;
        }
        CardPresence chip = pollIcc(mag, icc, picc, mode, listener);
        if (chip != null) {
            return chip;
        }
        return pollPicc(mag, icc, picc, mode, listener);
    }

    @Nullable
    private CardPresence pollMag(@Nullable IMag mag, @Nullable IIcc icc, @Nullable IPicc picc,
            byte mode, CardSearchListener listener) {
        if (!SearchMode.isSupportMag(mode) || mag == null) {
            return null;
        }
        try {
            if (!mag.isSwiped()) {
                return null;
            }
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
        } catch (Throwable t) {
            LogUtils.w(TAG, "MAG poll: " + t.getMessage());
        }
        return null;
    }

    @Nullable
    private CardPresence pollIcc(@Nullable IMag mag, @Nullable IIcc icc, @Nullable IPicc picc,
            byte mode, CardSearchListener listener) {
        if (!SearchMode.isSupportIcc(mode) || icc == null || iccDisabled) {
            return null;
        }
        try {
            boolean present = icc.detect(ICC_SLOT);
            if (!present) {
                if (iccCardPresent) {
                    resetIccSearchState();
                }
                return null;
            }
            iccCardPresent = true;

            if (isIccUnreadableTooLong()) {
                LogUtils.w(TAG, "ICC still no ATR after " + ICC_UNREADABLE_GRACE_MS
                        + "ms — handing to EMV so fallback rules apply");
                closeQuietly(mag, null, picc);
                CardPresence card = CardPresence.chip();
                listener.onChipDetected(card);
                return card;
            }
            if (System.currentTimeMillis() < iccNextInitAtMs) {
                return null;
            }

            byte[] atr = icc.init(ICC_SLOT);
            if (atr == null || atr.length == 0) {
                onIccInitFailed(icc, "empty ATR");
                return null;
            }
            closeQuietly(mag, null, picc);
            CardPresence card = CardPresence.chip();
            listener.onChipDetected(card);
            return card;
        } catch (IccDevException e) {
            if (e.getErrCode() == EIccDevException.ERROR_DISABLED.getErrCodeFromBasement()) {
                iccDisabled = true;
                LogUtils.w(TAG, "ICC reader disabled");
                return null;
            }
            // Native IccManager.iccInit throws IccException: 51 (no ATR / card mute) and the
            // DAL prints that stack to System.err itself before wrapping it as ICC#97.
            onIccInitFailed(icc, e.getErrCode() + " " + e.getErrMsg());
            return null;
        } catch (Throwable t) {
            onIccInitFailed(icc, t.getMessage());
            return null;
        }
    }

    private boolean isIccUnreadableTooLong() {
        return iccFirstFailureAtMs > 0
                && System.currentTimeMillis() - iccFirstFailureAtMs >= ICC_UNREADABLE_GRACE_MS;
    }

    private void onIccInitFailed(@Nullable IIcc icc, @Nullable String detail) {
        long now = System.currentTimeMillis();
        if (iccFirstFailureAtMs <= 0) {
            iccFirstFailureAtMs = now;
        }
        iccNextInitAtMs = now + ICC_RETRY_MS;
        if (icc != null) {
            try {
                icc.close(ICC_SLOT);
            } catch (Exception ignored) {
            }
        }
        if (!iccInitWarned) {
            iccInitWarned = true;
            LogUtils.w(TAG, "ICC init failed (" + detail + ") on " + describeDevice()
                    + ". Native IccException 51 / DAL ICC#97 = no ATR."
                    + " Slot power-cycled; reseat the chip or swipe.");
        }
    }

    private void resetIccSearchState() {
        iccCardPresent = false;
        iccFirstFailureAtMs = 0;
        iccNextInitAtMs = 0;
        iccInitWarned = false;
    }

    /** Model traits that explain a mute chip: no ICC module, or a shared mag/ICC slot. */
    private static String describeDevice() {
        try {
            ModelInfo info = ModelInfo.getInstance();
            return Build.MODEL + " (iccSupported=" + info.isSupportIcc()
                    + ", magIccConflict=" + info.isMagIccConflict() + ")";
        } catch (Throwable t) {
            return Build.MODEL;
        }
    }

    @Nullable
    private CardPresence pollPicc(@Nullable IMag mag, @Nullable IIcc icc, @Nullable IPicc picc,
            byte mode, CardSearchListener listener) {
        if (!SearchMode.isSupportInternalPicc(mode) || picc == null) {
            return null;
        }
        try {
            PiccCardInfo info = picc.detect(EDetectMode.EMV_AB);
            if (info == null) {
                return null;
            }
            closeQuietly(mag, icc, null);
            CardPresence card = CardPresence.contactless(info.getSerialInfo());
            listener.onContactlessDetected(card);
            return card;
        } catch (Throwable t) {
            LogUtils.w(TAG, "PICC poll: " + t.getMessage());
            return null;
        }
    }

    @Nullable
    private static IMag openMag(IDAL dal) {
        try {
            IMag mag = dal.getMag();
            mag.close();
            mag.open();
            mag.reset();
            return mag;
        } catch (Throwable t) {
            LogUtils.e(TAG, "MAG open failed (need " + PaxHardwarePermissions.MAGCARD + ")", t);
            return null;
        }
    }

    @Nullable
    private static IIcc openIcc(IDAL dal) {
        try {
            IIcc icc = dal.getIcc();
            icc.close(ICC_SLOT);
            return icc;
        } catch (Throwable t) {
            LogUtils.e(TAG, "ICC open failed (need " + PaxHardwarePermissions.ICC + ")", t);
            return null;
        }
    }

    @Nullable
    private static IPicc openPicc(IDAL dal) {
        try {
            IPicc picc = dal.getPicc(EPiccType.INTERNAL);
            picc.close();
            picc.open();
            return picc;
        } catch (Throwable t) {
            LogUtils.e(TAG, "PICC open failed (need " + PaxHardwarePermissions.PICC + ")", t);
            return null;
        }
    }

    private static byte toSearchMode(TransactionConfig config) {
        byte mode = 0;
        if (config.allowsChip()) {
            mode |= SearchMode.INSERT;
        }
        if (config.allowsContactless()) {
            mode |= SearchMode.INTERNAL_WAVE;
        }
        if (config.allowsMagstripe()) {
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
                icc.close(ICC_SLOT);
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
