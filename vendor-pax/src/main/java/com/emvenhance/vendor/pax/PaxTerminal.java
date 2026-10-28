package com.emvenhance.vendor.pax;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.param.EmvParams;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.emvenhance.network.SaleCommunicationBehavior;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.currency.CurrencyConverter;
import com.pax.commonlib.sp.SharedPrefUtil;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.export.ConfigKeyConstant;
import com.pax.configservice.impl.ConfigInit;
import com.pax.configservice.impl.ConfigParamService;
import com.pax.configservice.impl.EmvParamService;
import com.pax.dal.ICardReaderHelper;
import com.pax.dal.IDAL;
import com.pax.dal.entity.EPiccType;
import com.pax.dal.entity.EReaderType;
import com.pax.dal.entity.PollingResult;
import com.pax.dal.exceptions.EPiccDevException;
import com.pax.dal.exceptions.IccDevException;
import com.pax.dal.exceptions.MagDevException;
import com.pax.dal.exceptions.PiccDevException;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.poslib.model.ModelInfo;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class PaxTerminal extends PosTerminal {

    private static final String TAG = "PaxTerminal";
    private static final int SEARCH_TIMEOUT_MS = 30_000;
    private static final String KEY_EMV_CONFIG_INITIALIZED = "emv_config_initialized";

    private static final int ICC_SEARCH_MAX_ATTEMPTS = 3;
    private static final long ICC_POWER_SETTLE_MS = 200L;

    private static final long PICC_RESET_SETTLE_MS = 100L;

    private final PaxKernel kernel;

    @Nullable
    private volatile ICardReaderHelper activeCardReaderHelper;

    public PaxTerminal() {
        this(new PaxKernel(),
                new SaleCommunicationBehavior(BaseApplication.getAppContext(), ModelInfo.getInstance().getSN()),
                new PaxPrinter());
    }

    private PaxTerminal(PaxKernel kernel,
            CommunicationBehavior communication,
            PrinterBehavior printer) {
        super(new PaxEmvBehavior(kernel), communication, printer);
        this.kernel = kernel;
    }

    @Nullable
    EmvProcessParam kernelParams() {
        return EmvParamService.loaded();
    }

    @Override
    public String serialNumber() {
        return ModelInfo.getInstance().getSN();
    }

    @Override
    public String[] requiredPermissions() {
        return PaxHardwarePermissions.all();
    }

    @Override
    public List<String> countries() {
        List<String> countries = new ArrayList<>();
        CurrencyConverter.getSupportedLocaleList(countries);
        return countries;
    }

    @Override
    public String setCountry(String country) {
        new ConfigParamService().putString(ConfigKeyConstant.EDC_CURRENCY_LIST, country);
        CurrencyConverter.setDefCurrency(country);
        for (Locale locale : CurrencyConverter.getSupportedLocale()) {
            if (locale.getDisplayName(Locale.US).equals(country)) {
                try {
                    return Currency.getInstance(locale).getCurrencyCode();
                } catch (IllegalArgumentException e) {
                    return "?";
                }
            }
        }
        return "?";
    }

    @Override
    public EmvParams emvParams() {
        return new PaxEmvParams(this);
    }

    @Override
    protected void initializeVendor() {
        PaxHardwarePermissions.logGrantState();
        ensureEmvConfigInitialized();
        LogUtils.i(TAG, "PAX terminal initialized (ICardReaderHelper)");
    }

    private void ensureEmvConfigInitialized() {
        SharedPrefUtil prefs = new SharedPrefUtil(BaseApplication.getAppContext());
        if (prefs.getBoolean(KEY_EMV_CONFIG_INITIALIZED)) {
            LogUtils.i(TAG, "EMV config already initialized, skipping");
            return;
        }
        new Thread(() -> {
            new ConfigInit().init();
            prefs.putBoolean(KEY_EMV_CONFIG_INITIALIZED, true);
        }, "ConfigInit").start();
    }

    @Nullable
    @Override
    public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
        if (config.isManual()) {
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
                    listener);
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

    @Nullable
    private PollingResult pollUntilCardOrTimeout(ICardReaderHelper cardReaderHelper, IDAL dal,
            EReaderType readerType, CardSearchListener listener)
            throws MagDevException, IccDevException, PiccDevException {
        long deadline = SystemClock.elapsedRealtime() + SEARCH_TIMEOUT_MS;
        int iccAttempts = 0;
        while (true) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0 || isSearchCancelled()) {
                return null;
            }
            try {
                return cardReaderHelper.polling(readerType, (int) remaining);
            } catch (PiccDevException e) {
                if (isSearchCancelled() || !isRetryablePiccError(e.getErrCode())) {
                    throw e;
                }
                LogUtils.w(TAG, "PICC read failed during search, code=" + e.getErrCode() + " "
                        + e.getErrMsg() + " — resetting RF field and searching again");
                apduTrace().note("PICC", "search read failed: " + e.getErrCode() + " "
                        + e.getErrMsg() + " — searching again");
                listener.onSearchRetry(piccRetryMessage(e.getErrCode()));
                resetPicc(dal);
            } catch (IccDevException e) {
                iccAttempts++;
                if (iccAttempts >= ICC_SEARCH_MAX_ATTEMPTS || isSearchCancelled()) {
                    throw e;
                }
                LogUtils.w(TAG, "ICC error during search, code=" + e.getErrCode() + " (attempt "
                        + iccAttempts + "/" + ICC_SEARCH_MAX_ATTEMPTS
                        + ") — power-cycling ICC and retrying");
                apduTrace().note("ICC", "search read failed: " + e.getErrCode() + " "
                        + e.getErrMsg() + " — power-cycling and searching again");
                listener.onSearchRetry("Chip not read — remove the card and insert it again");
                try {
                    dal.getIcc().close((byte) 0);
                } catch (Exception ignored) {
                }
                if (!sleepQuietly(ICC_POWER_SETTLE_MS)) {
                    throw e;
                }
            }
        }
    }

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

    private static void resetPicc(IDAL dal) {
        try {
            dal.getPicc(EPiccType.INTERNAL).close();
        } catch (Exception ignored) {
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
            if (isSearchCancelled()) {
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
