package com.emvenhance.vendor.pax;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.HostDefaults;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.sp.SharedPrefUtil;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.impl.ConfigInit;
import com.pax.dal.ICardReaderHelper;
import com.pax.dal.IDAL;
import com.pax.dal.entity.EReaderType;
import com.pax.dal.entity.PollingResult;
import com.pax.dal.exceptions.IccDevException;
import com.pax.dal.exceptions.MagDevException;
import com.pax.dal.exceptions.PiccDevException;

/**
 * PAX POS terminal — uses {@link ICardReaderHelper} for card search and
 * creates {@link PaxEmvBehavior} for EMV processing.
 *
 * <p>Card detection delegates to the Neptune DAL's {@link ICardReaderHelper#polling},
 * which internally manages all readers (MAG / ICC / PICC) and returns a
 * {@link PollingResult} when a card is detected, the search times out, or is cancelled.
 * This replaces manual reader polling and matches the PAX reference demo app pattern.
 */
public class PaxTerminal extends PosTerminal {

    private static final String TAG = "PaxTerminal";
    private static final int SEARCH_TIMEOUT_MS = 60_000;
    private static final String KEY_EMV_CONFIG_INITIALIZED = "emv_config_initialized";

    private final PaxKernel kernel;

    @Nullable
    private volatile ICardReaderHelper activeCardReaderHelper;

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
        ensureEmvConfigInitialized();
        LogUtils.i(TAG, "PAX terminal initialized (ICardReaderHelper)");
    }

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

        EReaderType readerType = toReaderType(config);
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

            PollingResult result = cardReaderHelper.polling(readerType, SEARCH_TIMEOUT_MS);
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
            LogUtils.e(TAG, "searchCard failed", t);
            listener.onReaderError(t.getMessage() != null ? t.getMessage() : "Reader error");
            return null;
        } finally {
            activeCardReaderHelper = null;
        }
    }

    @Override
    protected void cancelCardSearch() {
        ICardReaderHelper helper = activeCardReaderHelper;
        if (helper != null) {
            helper.stopPolling();
        }
    }

    @Nullable
    private CardPresence handlePollingResult(PollingResult result, CardSearchListener listener) {
        if (result == null) {
            listener.onSearchTimeout();
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

    private static EReaderType toReaderType(TransactionConfig config) {
        boolean mag = config.allowsMagstripe();
        boolean icc = config.allowsChip();
        boolean picc = config.allowsContactless();

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
