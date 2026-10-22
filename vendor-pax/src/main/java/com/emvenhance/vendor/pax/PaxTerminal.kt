package com.emvenhance.vendor.pax

import android.os.SystemClock
import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.host.CommunicationBehavior
import com.emvenhance.core.host.PrinterBehavior
import com.emvenhance.core.param.EmvParams
import com.emvenhance.core.terminal.PosTerminal
import com.emvenhance.emvflow.runtime.EmvFlowRuntime
import com.emvenhance.network.SaleCommunicationBehavior
import com.pax.commonlib.application.BaseApplication
import com.pax.commonlib.currency.CurrencyConverter
import com.pax.commonlib.sp.SharedPrefUtil
import com.pax.commonlib.utils.LogUtils
import com.pax.configservice.export.ConfigKeyConstant
import com.pax.configservice.impl.ConfigInit
import com.pax.configservice.impl.ConfigParamService
import com.pax.dal.ICardReaderHelper
import com.pax.dal.IDAL
import com.pax.dal.entity.EPiccType
import com.pax.dal.entity.EReaderType
import com.pax.dal.entity.PollingResult
import com.pax.dal.exceptions.EPiccDevException
import com.pax.dal.exceptions.IccDevException
import com.pax.dal.exceptions.MagDevException
import com.pax.dal.exceptions.PiccDevException
import com.pax.emvbase.param.EmvProcessParam
import com.pax.poslib.model.ModelInfo
import java.util.Currency
import java.util.Locale

/**
 * PAX POS terminal — uses [ICardReaderHelper] for card search and creates [PaxEmvBehavior] for
 * EMV processing.
 *
 * Card detection delegates to the Neptune DAL's [ICardReaderHelper.polling], which internally
 * manages all readers (MAG / ICC / PICC) and returns a [PollingResult] when a card is detected,
 * the search times out, or is cancelled. This replaces manual reader polling and matches the PAX
 * reference demo app pattern. The poll blocks the transaction thread; a cancel stops it with
 * `stopPolling()`, never by interrupting the thread.
 */
class PaxTerminal private constructor(
    private val kernel: PaxKernel,
    communication: CommunicationBehavior,
    printer: PrinterBehavior,
) : PosTerminal(PaxEmvBehavior(kernel), communication, printer) {

    @Volatile
    private var activeCardReaderHelper: ICardReaderHelper? = null

    /**
     * Wired to [SaleCommunicationBehavior] (`orchestration/exchange` + `orchestration/sale`) — it
     * builds its headers via `HostHeaders.build`, the real confirmed contract, which needs this
     * terminal's own serial number ([ModelInfo.getSN]); `:network` stays vendor-agnostic, so
     * that's passed in here rather than fetched from inside it.
     * `RetrofitCommunicationBehavior` (`crypto/purchase`) is still available as the plain-JSON
     * alternative if this app ever needs to switch back.
     */
    constructor() : this(
        PaxKernel(),
        SaleCommunicationBehavior(BaseApplication.getAppContext(), ModelInfo.getInstance().sn),
        PaxPrinter(),
    )

    /**
     * The EMV parameters the kernel uses — its in-memory cache, loaded from the database once
     * (see EmvParamService#getCachedEmvParam). For [PaxEmvParams]; call it off the main thread,
     * since an empty cache loads from the database here.
     */
    internal fun kernelParams(): EmvProcessParam = kernel.params.cachedEmvParam

    override fun serialNumber(): String = ModelInfo.getInstance().sn

    /** The PAX Neptune hardware permissions — see [PaxHardwarePermissions]. */
    override fun requiredPermissions(): Array<String> = PaxHardwarePermissions.all()

    /** Every country/currency PAX's [CurrencyConverter] knows, by display name. */
    override fun countries(): List<String> {
        val countries = ArrayList<String>()
        CurrencyConverter.getSupportedLocaleList(countries)
        return countries
    }

    /**
     * Saves `country` as the terminal's currency setting (EDC_CURRENCY_LIST, read back on every
     * call) and applies it at once for the next amount formatted.
     */
    override fun setCountry(country: String): String {
        ConfigParamService().putString(ConfigKeyConstant.EDC_CURRENCY_LIST, country)
        CurrencyConverter.setDefCurrency(country)
        for (locale in CurrencyConverter.getSupportedLocale()) {
            if (locale.getDisplayName(Locale.US) == country) {
                return try {
                    Currency.getInstance(locale).currencyCode
                } catch (e: IllegalArgumentException) {
                    "?"
                }
            }
        }
        return "?"
    }

    override fun emvParams(): EmvParams = PaxEmvParams(this)

    override fun initializeVendor() {
        PaxHardwarePermissions.logGrantState()
        ensureEmvConfigInitialized()
        LogUtils.i(TAG, "PAX terminal initialized (ICardReaderHelper)")
    }

    override fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence? {
        if (config.isManual) {
            listener.onSearchStarted(config)
            //            listener.onManualEntrySelected(card);
            return CardPresence.manual(null)
        }

        val dal = EmvFlowRuntime.getDal()
        if (dal == null) {
            LogUtils.e(TAG, "DAL not ready")
            listener.onReaderError("DAL not ready")
            return null
        }

        val readerType = toReaderType(
            config.allowsMagstripe(),
            config.allowsChip() && kernel.contactReady,
            config.allowsContactless() && kernel.contactlessReady,
        )
        if (readerType == EReaderType.DEFAULT) {
            listener.onReaderError("No searchable entry mode enabled")
            return null
        }

        val cardReaderHelper = dal.cardReaderHelper
        if (cardReaderHelper == null) {
            listener.onReaderError("CardReaderHelper not available")
            return null
        }

        activeCardReaderHelper = cardReaderHelper
        return try {
            PaxHardwarePermissions.logGrantState()
            listener.onSearchStarted(config)

            val result = pollUntilCardOrTimeout(cardReaderHelper, dal, readerType, listener)
            handlePollingResult(result, listener)
        } catch (e: MagDevException) {
            LogUtils.e(TAG, "MAG error during search", e)
            listener.onReaderError("MAG: " + e.errMsg)
            null
        } catch (e: IccDevException) {
            LogUtils.e(TAG, "ICC error during search", e)
            listener.onReaderError("ICC: " + e.errMsg)
            null
        } catch (e: PiccDevException) {
            LogUtils.e(TAG, "PICC error during search", e)
            listener.onReaderError("PICC: " + e.errMsg)
            null
        } catch (t: Throwable) {
            LogUtils.e(TAG, "searchCard failed", t)
            listener.onReaderError(t.message ?: "Reader error")
            null
        } finally {
            activeCardReaderHelper = null
        }
    }

    /**
     * Polls until a card is read, the search is cancelled, or [SEARCH_TIMEOUT_MS] runs out
     * (returns `null` then, reported as a timeout). A failed read the cardholder can fix doesn't
     * end the transaction — the search carries on with whatever time is left, and the screen
     * says what to do:
     * - Contactless ([PiccDevException]): a tap too short or too far, two cards in the field, or
     *   an RF protocol/IO error — very common on a real tap. The RF field is reset before the
     *   next attempt. Other PICC errors (not open, no permission...) still fail.
     * - Chip ([IccDevException]): native `IccException: 51` (no ATR) means the chip didn't answer
     *   this time — mis-seated, or a mag-only card in the slot. Up to [ICC_SEARCH_MAX_ATTEMPTS]
     *   attempts, power-cycling the slot in between.
     */
    @Throws(MagDevException::class, IccDevException::class, PiccDevException::class)
    private fun pollUntilCardOrTimeout(
        cardReaderHelper: ICardReaderHelper,
        dal: IDAL,
        readerType: EReaderType,
        listener: CardSearchListener,
    ): PollingResult? {
        val deadline = SystemClock.elapsedRealtime() + SEARCH_TIMEOUT_MS
        var iccAttempts = 0
        while (true) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0 || isSearchCancelled()) {
                return null
            }
            try {
                return cardReaderHelper.polling(readerType, remaining.toInt())
            } catch (e: PiccDevException) {
                if (isSearchCancelled() || !isRetryablePiccError(e.errCode)) {
                    throw e
                }
                LogUtils.w(
                    TAG,
                    "PICC read failed during search, code=" + e.errCode + " " + e.errMsg +
                        " — resetting RF field and searching again",
                )
                apduTrace().note("PICC", "search read failed: " + e.errCode + " " + e.errMsg + " — searching again")
                listener.onSearchRetry(piccRetryMessage(e.errCode))
                resetPicc(dal)
            } catch (e: IccDevException) {
                iccAttempts++
                if (iccAttempts >= ICC_SEARCH_MAX_ATTEMPTS || isSearchCancelled()) {
                    throw e
                }
                LogUtils.w(
                    TAG,
                    "ICC error during search, code=" + e.errCode + " (attempt " + iccAttempts + "/" +
                        ICC_SEARCH_MAX_ATTEMPTS + ") — power-cycling ICC and retrying",
                )
                apduTrace().note(
                    "ICC",
                    "search read failed: " + e.errCode + " " + e.errMsg + " — power-cycling and searching again",
                )
                listener.onSearchRetry("Chip not read — remove the card and insert it again")
                try {
                    dal.icc.close(0.toByte())
                } catch (ignored: Exception) {
                    // Expected if the slot is already in a bad state — closing here is only to
                    // force a fresh power-on for the next attempt, not to succeed cleanly.
                }
                if (!sleepQuietly(ICC_POWER_SETTLE_MS)) {
                    throw e
                }
            }
        }
    }

    override fun cancelCardSearch() {
        activeCardReaderHelper?.stopPolling()
    }

    private fun handlePollingResult(result: PollingResult?, listener: CardSearchListener): CardPresence? {
        if (result == null) {
            if (isSearchCancelled()) {
                listener.onSearchCancelled()
            } else {
                listener.onSearchTimeout()
            }
            return null
        }

        when (result.operationType) {
            PollingResult.EOperationType.TIMEOUT -> {
                listener.onSearchTimeout()
                return null
            }
            PollingResult.EOperationType.CANCEL -> {
                listener.onSearchCancelled()
                return null
            }
            PollingResult.EOperationType.OK -> Unit
            else -> {
                listener.onSearchTimeout()
                return null
            }
        }

        val detectedReader = result.readerType
        if (detectedReader == null) {
            listener.onReaderError("Card detected but reader type is null")
            return null
        }

        return when (detectedReader) {
            EReaderType.MAG -> {
                var t1: String? = result.track1
                var t2: String? = result.track2
                var t3: String? = result.track3
                if (t2.isNullOrEmpty()) {
                    kernel.mag.magRead()
                    t1 = kernel.mag.track1
                    t2 = kernel.mag.track2
                    t3 = kernel.mag.track3
                }
                if (t2.isNullOrEmpty()) {
                    listener.onReaderError("Magnetic stripe read failed — no track 2 data")
                    return null
                }
                CardPresence.magstripe(t1, t2, t3).also(listener::onMagstripeDetected)
            }
            EReaderType.ICC -> CardPresence.chip().also(listener::onChipDetected)
            EReaderType.PICC, EReaderType.PICCEXTERNAL ->
                CardPresence.contactless(result.serialInfo).also(listener::onContactlessDetected)
            else -> {
                listener.onReaderError("Unsupported reader type: $detectedReader")
                null
            }
        }
    }

    private companion object {
        const val TAG = "PaxTerminal"

        /**
         * How long card search waits for a card before the transaction is declined. No EMV/PCI
         * rule fixes this; 30 s is the usual attended-POS value (PAX's demo used 60 s, too long
         * at a till).
         */
        const val SEARCH_TIMEOUT_MS = 30_000
        const val KEY_EMV_CONFIG_INITIALIZED = "emv_config_initialized"

        /**
         * Retry budget for ICC#97/"no ATR" during search — same tuning as
         * `PaxEmvBehavior#selectApplicationWithRetry`: power-cycle the slot, wait for VCC to
         * settle, retry a bounded number of times before giving up.
         */
        const val ICC_SEARCH_MAX_ATTEMPTS = 3
        const val ICC_POWER_SETTLE_MS = 200L

        /** Pause after dropping the RF field before polling again, so the card fully resets. */
        const val PICC_RESET_SETTLE_MS = 100L

        fun ensureEmvConfigInitialized() {
            val prefs = SharedPrefUtil(BaseApplication.getAppContext())
            if (prefs.getBoolean(KEY_EMV_CONFIG_INITIALIZED)) {
                LogUtils.i(TAG, "EMV config already initialized, skipping")
                return
            }
            ConfigInit().init()
            prefs.putBoolean(KEY_EMV_CONFIG_INITIALIZED, true)
        }

        /** Contactless read failures a second tap can fix — `EPiccDevException` basement codes. */
        fun isRetryablePiccError(code: Int): Boolean = code in listOf(
            EPiccDevException.PICC_ERR_NOT_SEARCH_CARD,
            EPiccDevException.PICC_ERR_CARD_TOO_MANY,
            EPiccDevException.PICC_ERR_PROTOCOL1,
            EPiccDevException.PICC_ERR_NO_ACTIVATION,
            EPiccDevException.PICC_ERR_MUTI_CARD,
            EPiccDevException.PICC_ERR_TIMEOUT,
            EPiccDevException.PICC_ERR_PROTOCOL2,
            EPiccDevException.PICC_ERR_IO,
            EPiccDevException.PICC_ERR_CARD_SENSE,
            EPiccDevException.PICC_ERR_CARD_STATUS,
        ).map { it.errCodeFromBasement }

        fun piccRetryMessage(code: Int): String =
            if (code == EPiccDevException.PICC_ERR_MUTI_CARD.errCodeFromBasement ||
                code == EPiccDevException.PICC_ERR_CARD_TOO_MANY.errCodeFromBasement
            ) {
                "More than one card — tap only one card"
            } else {
                "Card not read — tap again and hold the card still"
            }

        /** Drops the RF field so the next poll starts a fresh contactless activation. */
        fun resetPicc(dal: IDAL) {
            try {
                dal.getPicc(EPiccType.INTERNAL).close()
            } catch (ignored: Exception) {
                // Already closed, or in a bad state — the next polling() reopens it either way.
            }
            sleepQuietly(PICC_RESET_SETTLE_MS)
        }

        fun sleepQuietly(ms: Long): Boolean = try {
            Thread.sleep(ms)
            true
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        fun toReaderType(mag: Boolean, icc: Boolean, picc: Boolean): EReaderType = when {
            mag && icc && picc -> EReaderType.MAG_ICC_PICC
            mag && icc -> EReaderType.MAG_ICC
            mag && picc -> EReaderType.MAG_PICC
            icc && picc -> EReaderType.ICC_PICC
            mag -> EReaderType.MAG
            icc -> EReaderType.ICC
            picc -> EReaderType.PICC
            else -> EReaderType.DEFAULT
        }
    }
}
