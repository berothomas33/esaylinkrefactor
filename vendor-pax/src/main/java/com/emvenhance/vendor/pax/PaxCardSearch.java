package com.emvenhance.vendor.pax;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.emv.ApduTrace;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.ICardReaderHelper;
import com.pax.dal.IDAL;
import com.pax.dal.entity.EPiccType;
import com.pax.dal.entity.EReaderType;
import com.pax.dal.entity.PollingResult;
import com.pax.dal.exceptions.EPiccDevException;
import com.pax.dal.exceptions.IccDevException;
import com.pax.dal.exceptions.MagDevException;
import com.pax.dal.exceptions.PiccDevException;
import java.util.function.BooleanSupplier;

/**
 * PAX card search — the {@code SEARCH_CARD} step's work, run by {@link PaxEmvBehavior#onSearchCard}.
 *
 * <p>Card detection delegates to the Neptune DAL's {@link ICardReaderHelper#polling}, which
 * internally manages all readers (MAG / ICC / PICC) and returns a {@link PollingResult} when a
 * card is detected, the search times out, or is cancelled. This matches the PAX reference demo
 * app pattern.
 */
final class PaxCardSearch {

    private static final String TAG = "PaxCardSearch";
    /**
     * How long card search waits for a card before the transaction is declined. No EMV/PCI rule
     * fixes this; 30 s is the usual attended-POS value (PAX's demo used 60 s, too long at a till).
     */
    private static final int SEARCH_TIMEOUT_MS = 30_000;

    /**
     * Retry budget for ICC#97/"no ATR" during search — same tuning as
     * {@code PaxEmvBehavior#selectApplicationWithRetry}: power-cycle the slot, wait for VCC to
     * settle, retry a bounded number of times before giving up.
     */
    private static final int ICC_SEARCH_MAX_ATTEMPTS = 3;
    private static final long ICC_POWER_SETTLE_MS = 200L;

    /** Pause after dropping the RF field before polling again, so the card fully resets. */
    private static final long PICC_RESET_SETTLE_MS = 100L;

    private final PaxKernel kernel;
    /** Whether the search should stop: its transaction is over (the behavior's {@code !isRunning()}). */
    private final BooleanSupplier cancelled;

    @Nullable
    private volatile ICardReaderHelper activeCardReaderHelper;

    PaxCardSearch(PaxKernel kernel, BooleanSupplier cancelled) {
        this.kernel = kernel;
        this.cancelled = cancelled;
    }

    /**
     * Polls for a card until one is read, the search times out or fails, or it is cancelled, and
     * reports it through {@code listener} — exactly one of found / timeout / cancelled / error.
     * A card found runs the EMV inside that callback.
     *
     * @return the card found, or {@code null}
     */
    @Nullable
    CardPresence search(TransactionConfig config, CardSearchListener listener, ApduTrace trace) {
        if (config.isManual()) {
            // Manual entry isn't implemented (no PAN entry screen): end it with a clear ERROR. A
            // card returned with no listener callback ended as "Transaction ended without an
            // outcome"; starting EMV on it would go online with no PAN.
            listener.onSearchStarted(config);
            listener.onReaderError("Manual entry is not available on this terminal yet");
            return null;
        }

        IDAL dal = EmvFlowRuntime.getDal();
        if (dal == null) {
            LogUtils.e(TAG, "DAL not ready");
            listener.onReaderError("DAL not ready");
            return null;
        }

        EReaderType readerType = toReaderType(config.allowsMagstripe(),
                config.allowsChip() && kernel.contactReady,
                config.allowsContactless() && kernel.contactlessReady);
        if (readerType == EReaderType.DEFAULT) {
            listener.onReaderError("No searchable entry mode enabled");
            return null;
        }

        ICardReaderHelper cardReaderHelper = dal.getCardReaderHelper();
        if (cardReaderHelper == null) {
            listener.onReaderError("CardReaderHelper not available");
            return null;
        }

        activeCardReaderHelper = cardReaderHelper;
        try {
            PaxHardwarePermissions.logGrantState();
            listener.onSearchStarted(config);

            PollingResult result = pollUntilCardOrTimeout(cardReaderHelper, dal, readerType,
                    listener, trace);
            return handlePollingResult(result, listener);
        } catch (MagDevException e) {
            LogUtils.e(TAG, "MAG error during search", e);
            listener.onReaderError("MAG: " + e.getErrMsg());
            return null;
        } catch (IccDevException e) {
            LogUtils.e(TAG, "ICC error during search", e);
            listener.onReaderError("ICC: " + e.getErrMsg());
            return null;
        } catch (PiccDevException e) {
            LogUtils.e(TAG, "PICC error during search", e);
            listener.onReaderError("PICC: " + e.getErrMsg());
            return null;
        } catch (Throwable t) {
            LogUtils.e(TAG, "card search failed", t);
            listener.onReaderError(t.getMessage() != null ? t.getMessage() : "Reader error");
            return null;
        } finally {
            activeCardReaderHelper = null;
        }
    }

    /**
     * Polls until a card is read, the search is cancelled, or {@link #SEARCH_TIMEOUT_MS} runs out
     * (returns {@code null} then, reported as a timeout). A failed read the cardholder can fix
     * doesn't end the transaction — the search carries on with whatever time is left, and the
     * screen says what to do:
     * <ul>
     *   <li>Contactless ({@link PiccDevException}): a tap too short or too far, two cards in the
     *       field, or an RF protocol/IO error — very common on a real tap. The RF field is reset
     *       before the next attempt. Other PICC errors (not open, no permission...) still fail.
     *   <li>Chip ({@link IccDevException}): native {@code IccException: 51} (no ATR) means the chip
     *       didn't answer this time — mis-seated, or a mag-only card in the slot. Up to
     *       {@link #ICC_SEARCH_MAX_ATTEMPTS} attempts, power-cycling the slot in between.
     * </ul>
     */
    @Nullable
    private PollingResult pollUntilCardOrTimeout(ICardReaderHelper cardReaderHelper, IDAL dal,
            EReaderType readerType, CardSearchListener listener, ApduTrace trace)
            throws MagDevException, IccDevException, PiccDevException {
        long deadline = SystemClock.elapsedRealtime() + SEARCH_TIMEOUT_MS;
        int iccAttempts = 0;
        while (true) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0 || cancelled.getAsBoolean()) {
                return null;
            }
            try {
                return cardReaderHelper.polling(readerType, (int) remaining);
            } catch (PiccDevException e) {
                if (cancelled.getAsBoolean() || !isRetryablePiccError(e.getErrCode())) {
                    throw e;
                }
                LogUtils.w(TAG, "PICC read failed during search, code=" + e.getErrCode() + " "
                        + e.getErrMsg() + " — resetting RF field and searching again");
                trace.note("PICC", "search read failed: " + e.getErrCode() + " "
                        + e.getErrMsg() + " — searching again");
                listener.onSearchRetry(piccRetryMessage(e.getErrCode()));
                resetPicc(dal);
            } catch (IccDevException e) {
                iccAttempts++;
                if (iccAttempts >= ICC_SEARCH_MAX_ATTEMPTS || cancelled.getAsBoolean()) {
                    throw e;
                }
                LogUtils.w(TAG, "ICC error during search, code=" + e.getErrCode() + " (attempt "
                        + iccAttempts + "/" + ICC_SEARCH_MAX_ATTEMPTS
                        + ") — power-cycling ICC and retrying");
                trace.note("ICC", "search read failed: " + e.getErrCode() + " "
                        + e.getErrMsg() + " — power-cycling and searching again");
                listener.onSearchRetry("Chip not read — remove the card and insert it again");
                try {
                    dal.getIcc().close((byte) 0);
                } catch (Exception ignored) {
                    // Expected if the slot is already in a bad state — closing here is only to
                    // force a fresh power-on for the next attempt, not to succeed cleanly.
                }
                if (!sleepQuietly(ICC_POWER_SETTLE_MS)) {
                    throw e;
                }
            }
        }
    }

    /** Contactless read failures a second tap can fix — {@code EPiccDevException} basement codes. */
    private static boolean isRetryablePiccError(int code) {
        return code == EPiccDevException.PICC_ERR_NOT_SEARCH_CARD.getErrCodeFromBasement()
                || code == EPiccDevException.PICC_ERR_CARD_TOO_MANY.getErrCodeFromBasement()
                || code == EPiccDevException.PICC_ERR_PROTOCOL1.getErrCodeFromBasement()
                || code == EPiccDevException.PICC_ERR_NO_ACTIVATION.getErrCodeFromBasement()
                || code == EPiccDevException.PICC_ERR_MUTI_CARD.getErrCodeFromBasement()
                || code == EPiccDevException.PICC_ERR_TIMEOUT.getErrCodeFromBasement()
                || code == EPiccDevException.PICC_ERR_PROTOCOL2.getErrCodeFromBasement()
                || code == EPiccDevException.PICC_ERR_IO.getErrCodeFromBasement()
                || code == EPiccDevException.PICC_ERR_CARD_SENSE.getErrCodeFromBasement()
                || code == EPiccDevException.PICC_ERR_CARD_STATUS.getErrCodeFromBasement();
    }

    private static String piccRetryMessage(int code) {
        if (code == EPiccDevException.PICC_ERR_MUTI_CARD.getErrCodeFromBasement()
                || code == EPiccDevException.PICC_ERR_CARD_TOO_MANY.getErrCodeFromBasement()) {
            return "More than one card — tap only one card";
        }
        return "Card not read — tap again and hold the card still";
    }

    /** Drops the RF field so the next poll starts a fresh contactless activation. */
    private static void resetPicc(IDAL dal) {
        try {
            dal.getPicc(EPiccType.INTERNAL).close();
        } catch (Exception ignored) {
            // Already closed, or in a bad state — the next polling() reopens it either way.
        }
        sleepQuietly(PICC_RESET_SETTLE_MS);
    }

    private static boolean sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Stops a poll in progress (any thread): it returns, reported as cancelled. */
    void stop() {
        ICardReaderHelper helper = activeCardReaderHelper;
        if (helper != null) {
            helper.stopPolling();
        }
    }

    @Nullable
    private CardPresence handlePollingResult(PollingResult result, CardSearchListener listener) {
        if (result == null) {
            if (cancelled.getAsBoolean()) {
                listener.onSearchCancelled();
            } else {
                listener.onSearchTimeout();
            }
            return null;
        }

        PollingResult.EOperationType operation = result.getOperationType();
        if (operation == PollingResult.EOperationType.TIMEOUT) {
            listener.onSearchTimeout();
            return null;
        }
        if (operation == PollingResult.EOperationType.CANCEL) {
            listener.onSearchCancelled();
            return null;
        }
        if (operation != PollingResult.EOperationType.OK) {
            listener.onSearchTimeout();
            return null;
        }

        EReaderType detectedReader = result.getReaderType();
        if (detectedReader == null) {
            listener.onReaderError("Card detected but reader type is null");
            return null;
        }

        switch (detectedReader) {
            case MAG: {
                String t1 = result.getTrack1();
                String t2 = result.getTrack2();
                String t3 = result.getTrack3();
                if (t2 == null || t2.isEmpty()) {
                    kernel.mag.magRead();
                    t1 = kernel.mag.getTrack1();
                    t2 = kernel.mag.getTrack2();
                    t3 = kernel.mag.getTrack3();
                }
                if (t2 == null || t2.isEmpty()) {
                    listener.onReaderError("Magnetic stripe read failed — no track 2 data");
                    return null;
                }
                CardPresence card = CardPresence.magstripe(t1, t2, t3);
                listener.onMagstripeDetected(card);
                return card;
            }

            case ICC: {
                CardPresence card = CardPresence.chip();
                listener.onChipDetected(card);
                return card;
            }

            case PICC:
            case PICCEXTERNAL: {
                byte[] serialInfo = result.getSerialInfo();
                CardPresence card = CardPresence.contactless(serialInfo);
                listener.onContactlessDetected(card);
                return card;
            }

            default:
                listener.onReaderError("Unsupported reader type: " + detectedReader);
                return null;
        }
    }

    private static EReaderType toReaderType(boolean mag, boolean icc, boolean picc) {
        if (mag && icc && picc) {
            return EReaderType.MAG_ICC_PICC;
        }
        if (mag && icc) {
            return EReaderType.MAG_ICC;
        }
        if (mag && picc) {
            return EReaderType.MAG_PICC;
        }
        if (icc && picc) {
            return EReaderType.ICC_PICC;
        }
        if (mag) {
            return EReaderType.MAG;
        }
        if (icc) {
            return EReaderType.ICC;
        }
        if (picc) {
            return EReaderType.PICC;
        }
        return EReaderType.DEFAULT;
    }
}
