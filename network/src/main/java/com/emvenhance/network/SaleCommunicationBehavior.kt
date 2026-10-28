package com.emvenhance.network

import android.content.Context
import android.util.Log
import com.emvenhance.core.card.EmvTransactionResult
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.host.CommunicationBehavior
import com.emvenhance.network.crypto.AesEnvelopeCrypto
import com.emvenhance.network.crypto.RsaPublicKeyEncryptor
import com.emvenhance.network.model.ExchangeRequest
import com.emvenhance.network.model.ExchangeResponse
import com.emvenhance.network.model.GeneralRequest
import com.emvenhance.network.model.GeneralResponse
import com.emvenhance.network.model.RefundRequest
import com.emvenhance.network.model.SaleExtraData
import com.emvenhance.network.model.SaleRequest
import com.emvenhance.network.model.SaleResponse
import com.emvenhance.network.onboarding.OnboardingClient
import com.emvenhance.network.onboarding.OnboardingState
import com.emvenhance.network.tlv.IssuerResponseFields
import com.google.gson.Gson
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/**
 * Real [CommunicationBehavior] for the encrypted-envelope sale flow — `orchestration/exchange`
 * (establishes a fresh per-transaction TEK, and forwards the PEK if online PIN was collected) then
 * `orchestration/sale` (the actual authorization), replacing the old project's
 * `SaleActivity#callExchangeProcess`/`#callSaleProcess` pair. (Both paths were first guessed as
 * `cacore/exchange`/`cacore/sale` from `SaleActivity`'s field names — wrong, and confirmed wrong
 * by a real 400 Bad Request in testing; `HostApiConnection`'s real `EmvApiConnection` source,
 * found afterward, has the correct paths — `cacore/sale` does exist, but as a separate, simpler
 * test endpoint.)
 *
 * Deliberately narrower than the old project's `SaleActivity`, per the EMV-flow split this
 * codebase already made (`EmvEngine` + a vendor `EmvBehavior` own card reading; this class only
 * owns going online once [TransactionConfig.emvResult] is populated):
 * - `new MomknPayScenario().sale(...)` (EMV/card-reading via the old vendor SDK) has no
 *   counterpart here — [TransactionConfig.emvResult] is expected to already be populated by the
 *   vendor `EmvBehavior` before [authorize] is ever called; see `PaxEmvBehavior#startOnlineProcess`.
 * - Offline pending-transaction bookkeeping (`OfflineViewModel#addTransaction`/
 *   `#deleteTransaction`), the Mastercard-CLSS single-tap EasyLink retry, receipt printing, the
 *   reverse-on-failure call, the acknowledgement call, and the PENDING-status retry loop are all
 *   out of scope — a PENDING or failed envelope surfaces as a thrown [SaleException], once, with
 *   no retry (`PosTerminal.authorize` turns it into online failed).
 *
 * `GeneralRequest`/`ExchangeRequest`/`SaleRequest`/their response counterparts are now confirmed
 * against the real classes (found in an uploaded `model_layer.rar`) — see each model class's own
 * doc for exactly what changed from the first, setter-call-reconstructed version. Headers come
 * from [HostHeaders.buildAuthenticated] — post-onboarding, bearer-token mode, using [sn] (this
 * terminal's own serial number, passed in by `PaxTerminal`, which has PAX's `ModelInfo`; this
 * module stays vendor-agnostic, so it can't fetch that itself) and a token from
 * [OnboardingClient.ensureValidAccessToken] (refreshed first if near/past expiry, rather than the
 * raw `onboardingState.getAccessToken()` this used to read directly — that path reused whatever
 * token onboarding first minted forever, which is exactly how a real terminal started getting
 * `"Jwt expired"` from the host). Only a captured `tmsFileDownload` call confirmed that mode is
 * what a real authenticated request looks like — `orchestration/exchange`/`orchestration/sale`
 * using the same mode is a reasonable inference, not independently confirmed.
 *
 * The old project supplied a `paymentAsyncID` from whatever aggregator launched `SaleActivity` —
 * this codebase has no such caller, so a fresh [UUID] is generated per [authorize] call and
 * reused across both the exchange and sale requests instead.
 *
 * Every step suspends (Retrofit `suspend` calls) and runs sequentially; a failed step throws.
 */
class SaleCommunicationBehavior(
    private val connection: HostApiConnection,
    private val onboarding: OnboardingClient,
    private val onboardingState: OnboardingState,
    private val hostSettings: HostSettings,
    private val sn: String,
) : CommunicationBehavior {

    constructor(context: Context, sn: String) : this(
        HostApiClient.create(), OnboardingClient(), OnboardingState(context), HostSettings(context), sn,
    )

    /**
     * Sends the transaction to the host by its type: SALE → `orchestration/sale`, REFUND →
     * `orchestration/refund`, each after its own `orchestration/exchange`. Anything that can't go
     * online — a type with no host request yet, a refund without the original reference number —
     * is [AuthResult.failed] online failed: this terminal approves nothing offline, and a type is
     * never sent as another (a refund sent as a PURCHASE would charge the card).
     */
    override suspend fun authorize(config: TransactionConfig): AuthResult {
        refusal(config)?.let { return it }
        val emvResult = config.emvResult ?: throw SaleException(
            "No EmvTransactionResult on TransactionConfig — the vendor EmvBehavior must " +
                "attach one (see TransactionConfig#withEmvResult) before going online",
        )
        val hostPublicKey = onboardingState.getPublicKey() ?: throw SaleException(
            "No host public key on file — onboarding must complete before a TEK can be wrapped for the host",
        )
        if (onboardingState.getAccessToken() == null) {
            throw SaleException("No access token on file — onboarding must complete before a sale can go online")
        }

        val accessToken = onboarding.ensureValidAccessToken(HostHeaders.build(sn), onboardingState)
        return goOnline(HostHeaders.buildAuthenticated(sn, accessToken), config, emvResult, hostPublicKey)
    }

    private suspend fun goOnline(
        headers: Map<String, String>,
        config: TransactionConfig,
        emvResult: EmvTransactionResult,
        hostPublicKey: String,
    ): AuthResult {
        val tek = ByteArray(TEK_LENGTH_BYTES)
        SecureRandom().nextBytes(tek)
        val transactionKeyEncrypted = try {
            RsaPublicKeyEncryptor.encryptToHex(hostPublicKey, tek)
        } catch (e: GeneralSecurityException) {
            throw SaleException("Failed to RSA-encrypt the transaction key (TEK)", e)
        }

        val asyncRequestId = UUID.randomUUID().toString()

        val refund = config.type == TransactionType.REFUND
        val exchangeType = if (refund) TRANSACTION_TYPE_REFUND else TRANSACTION_TYPE_SALE
        exchange(headers, config, emvResult, tek, transactionKeyEncrypted, asyncRequestId, exchangeType)
        val response = if (refund) {
            refund(headers, config, emvResult, tek, asyncRequestId)
        } else {
            sale(headers, config, emvResult, tek, asyncRequestId)
        }
        return toAuthResult(response).withAsyncRequestId(asyncRequestId)
    }

    private suspend fun exchange(
        headers: Map<String, String>,
        config: TransactionConfig,
        emvResult: EmvTransactionResult,
        tek: ByteArray,
        transactionKeyEncrypted: String,
        asyncRequestId: String,
        transactionType: String,
    ): ExchangeResponse {
        val cvm = cvmCode(config, emvResult)
        val exchangeRequest = ExchangeRequest(asyncRequestId, emvResult.pan, cvm, transactionType)

        val encSerializedRequest = try {
            val serialized = GSON.toJson(exchangeRequest)
            logSerialized("EXCHANGE_REQUEST", serialized)
            AesEnvelopeCrypto.encrypt(tek, serialized)
        } catch (e: GeneralSecurityException) {
            throw SaleException("Failed to AES-encrypt the exchange request", e)
        }

        val request = GeneralRequest.forExchange(
            encSerializedRequest, asyncRequestId, transactionKeyEncrypted, pinKeyToSend(config), transactionType,
        )

        return decryptEnvelope(connection.exchange(headers, request), tek, "Exchange", ExchangeResponse::class.java)
    }

    private suspend fun sale(
        headers: Map<String, String>,
        config: TransactionConfig,
        emvResult: EmvTransactionResult,
        tek: ByteArray,
        asyncRequestId: String,
    ): SaleResponse {
        val saleRequest = buildSaleRequest(config, emvResult)

        val encSerializedRequest = try {
            val serialized = GSON.toJson(saleRequest)
            logSerialized("SALE_REQUEST", serialized)
            logSerialized("CHIP_DATA (field 55 sent)", config.iccData.toString())
            AesEnvelopeCrypto.encrypt(tek, serialized)
        } catch (e: GeneralSecurityException) {
            throw SaleException("Failed to AES-encrypt the sale request", e)
        }

        val request = GeneralRequest.forSale(encSerializedRequest, asyncRequestId, pinBlockToSend(config))

        // orchestration/sale (unlike exchange) 400s without mToken — see HostAppKeys#MTOKEN's
        // doc for what this is. The value saved on the onboarding screen wins over the fixed
        // build value (HostSettings), so an expired token can be replaced on the device.
        val saleHeaders = HashMap(headers)
        saleHeaders["mToken"] = hostSettings.getMToken()

        return decryptEnvelope(connection.sale(saleHeaders, request), tek, "Sale", SaleResponse::class.java)
    }

    /** `orchestration/refund` — same envelope and headers as [sale]. */
    private suspend fun refund(
        headers: Map<String, String>,
        config: TransactionConfig,
        emvResult: EmvTransactionResult,
        tek: ByteArray,
        asyncRequestId: String,
    ): SaleResponse {
        val refundRequest = buildRefundRequest(config, emvResult)

        val encSerializedRequest = try {
            val serialized = GSON.toJson(refundRequest)
            logSerialized("REFUND_REQUEST", serialized)
            logSerialized("CHIP_DATA (field 55 sent)", config.iccData.toString())
            AesEnvelopeCrypto.encrypt(tek, serialized)
        } catch (e: GeneralSecurityException) {
            throw SaleException("Failed to AES-encrypt the refund request", e)
        }

        val request = GeneralRequest.forSale(encSerializedRequest, asyncRequestId, pinBlockToSend(config))

        val refundHeaders = HashMap(headers)
        refundHeaders["mToken"] = hostSettings.getMToken()

        // The old RefundResponse has SaleResponse's fields (responseCode, responseMessage,
        // authCode, chipData, referenceNumber, ...), so it's decoded the same way.
        return decryptEnvelope(connection.refund(refundHeaders, request), tek, "Refund", SaleResponse::class.java)
    }

    /** The card fields [SaleRequest] and [RefundRequest] share. */
    private class CardFields {
        var expirationMonth = 0
        var expirationYear = 0
        var trailer = ""
    }

    companion object {
        private const val SUCCESS_STATUS_CODE = 200
        private const val TEK_LENGTH_BYTES = 16

        /**
         * `TransactionTypes.SALE.getType()` — confirmed against the old project's real
         * `TransactionTypes` enum source (its constants are `(id, type)` pairs, and the `type`
         * string doesn't always match the Java identifier — `SALE(1, "PURCHASE")`, whereas e.g.
         * `VOID(2, "VOID")` does). The previous guess here, plain `"SALE"`, looked reasonable (a
         * real captured example confirmed `VOID` for `VoidInquiryRequest`, and this field has the
         * same shape) but wasn't this one value — the host's
         * `cacore.transaction_log.transaction_type` column has a `CHECK` constraint that only a
         * real device log caught, rejecting `"SALE"` outright.
         */
        private const val TRANSACTION_TYPE_SALE = "PURCHASE"

        /** `TransactionTypes.REFUND.getType()` in the old project. */
        private const val TRANSACTION_TYPE_REFUND = "REFUND"

        private const val TAG = "SaleComm"

        /** Logcat truncates a single line at ~4000 characters. */
        private const val LOG_CHUNK = 3500

        /**
         * CVM codes — see [cvmCode]. Both PIN codes are confirmed by real successful old-app
         * sales: `"cvm":2` with `"pinEnterMode":"ONLINE"`, and `"cvm":3` with
         * `"pinEnterMode":"OFFLINE"` (Mastercard chip, offline PIN). [CVM_NONE] = 0 is not
         * confirmed.
         */
        private const val CVM_NONE = 0
        private const val CVM_ONLINE_PIN = 2
        private const val CVM_OFFLINE_PIN = 3

        /** `netAmount`: the old app's approved sale sent `0.0` next to its amount. */
        private const val NET_AMOUNT = 0.0
        private const val MASTERCARD_RID = "A000000004"

        private val GSON = Gson()

        /**
         * Why `config` can't go online at all, as online failed — `null` when it can. Checked
         * first, before anything touches the network or the stored onboarding state.
         */
        internal fun refusal(config: TransactionConfig): AuthResult? {
            val type = config.type
            if (type == TransactionType.REFUND) {
                val reference = config.referenceNumber
                if (reference == null || reference.trim().isEmpty()) {
                    Log.w(TAG, "Refund without the original reference number — not sent to the host")
                    return AuthResult.failed("Refund needs the original sale's reference number")
                }
            } else if (type != TransactionType.SALE) {
                // CASH_IN (POS-as-ATM deposit: atm/exchange + atm/deposit) has no request body
                // yet — the old app's screen that built it wasn't shared.
                Log.w(TAG, "$type has no host request yet — not sent to the host")
                return AuthResult.failed(type.label + " isn't supported online yet")
            }
            return null
        }

        private fun <T> decryptEnvelope(
            response: GeneralResponse,
            tek: ByteArray,
            step: String,
            type: Class<T>,
        ): T {
            val enc = response.data?.encSerializedResponse
            if (response.statusCode != SUCCESS_STATUS_CODE || enc == null) {
                val message = response.message ?: "$step failed"
                throw SaleException("$step failed (" + response.statusCode + "): " + message)
            }
            try {
                val decrypted = AesEnvelopeCrypto.decrypt(tek, enc)
                logSerialized(step.uppercase(Locale.US) + "_RESPONSE", decrypted)
                return GSON.fromJson(decrypted, type)
            } catch (e: GeneralSecurityException) {
                throw SaleException("Failed to decrypt the $step response", e)
            }
        }

        /**
         * Logs a request's JSON before it's AES-encrypted into `encSerializedRequest`, or a
         * response's after decryption — OkHttp's log only shows the encrypted envelope. Same as
         * the old app's `"SALE_REQUEST:Serialized: "` log. Filter Logcat by [TAG].
         *
         * Debug builds only: the plain JSON holds the PAN, chip data and track 2 trailer. Split
         * into chunks because Logcat truncates a line at about 4000 characters.
         */
        private fun logSerialized(label: String, json: String) {
            if (!BuildConfig.DEBUG) {
                return
            }
            val header = "$label:Serialized: "
            if (json.length <= LOG_CHUNK) {
                Log.i(TAG, header + json)
                return
            }
            val parts = (json.length + LOG_CHUNK - 1) / LOG_CHUNK
            for (i in 0 until parts) {
                val from = i * LOG_CHUNK
                Log.i(
                    TAG,
                    header + "(" + (i + 1) + "/" + parts + ") " +
                        json.substring(from, minOf(json.length, from + LOG_CHUNK)),
                )
            }
        }

        private fun cardFields(emvResult: EmvTransactionResult): CardFields {
            val fields = CardFields()
            val expDate = emvResult.expDate
            if (expDate != null && expDate.length >= 4) {
                try {
                    fields.expirationYear = expDate.substring(0, 2).toInt()
                    fields.expirationMonth = expDate.substring(2, 4).toInt()
                } catch (ignored: NumberFormatException) {
                    // Leave both at 0 — matches the old project's getSaleRequest(), which also
                    // swallows a malformed expiry rather than failing the whole request.
                }
            }

            val pan = emvResult.pan
            val track2 = emvResult.track2
            if (track2 != null && pan != null && expDate != null) {
                val prefix = pan + "D" + expDate
                if (track2.length > prefix.length && track2.startsWith(prefix)) {
                    fields.trailer = track2.substring(prefix.length)
                }
            }
            return fields
        }

        private fun extraDataJson(config: TransactionConfig, emvResult: EmvTransactionResult): String {
            val extraData = SaleExtraData(
                emvResult.aid ?: "",
                emvResult.emvAppName ?: "",
                emvResult.issuerName,
                pinEnterMode(config, emvResult),
                emvResult.cardHolderName,
                cardTypeLabel(emvResult.entryMethod),
            )
            return GSON.toJson(extraData)
        }

        internal fun buildSaleRequest(config: TransactionConfig, emvResult: EmvTransactionResult): SaleRequest {
            val card = cardFields(emvResult)

            // SaleRequest#amount is a major-currency-unit double (e.g. 10.50), not
            // TransactionConfig#amountMinor's minor-unit long (cents) — see SaleRequest's doc for
            // how that was confirmed.
            val amountMajor = config.amountMinor / 100.0

            return SaleRequest(
                amountMajor,
                NET_AMOUNT,
                cvmCode(config, emvResult),
                emvResult.pan,
                pinBlockToSend(config),
                config.iccData,
                card.expirationMonth,
                card.expirationYear,
                card.trailer,
                PosEntryModeCodes.forEntryMethod(emvResult.entryMethod),
                cardType(emvResult),
                dcc(emvResult),
                extraDataJson(config, emvResult),
            )
        }

        /** Same card fields as [buildSaleRequest], plus the original sale's reference number. */
        internal fun buildRefundRequest(config: TransactionConfig, emvResult: EmvTransactionResult): RefundRequest {
            val card = cardFields(emvResult)
            return RefundRequest(
                config.referenceNumber?.trim() ?: "",
                config.amountMinor / 100.0,
                cvmCode(config, emvResult),
                emvResult.pan,
                pinBlockToSend(config),
                config.iccData,
                card.expirationMonth,
                card.expirationYear,
                PosEntryModeCodes.forEntryMethod(emvResult.entryMethod),
                cardType(emvResult),
                card.trailer,
                dcc(emvResult),
                null,
                extraDataJson(config, emvResult),
            )
        }

        /**
         * The old project's real `PinEnterMode` enum/indices weren't shared — this derives a CVM
         * code from what [EmvTransactionResult]/[TransactionConfig] actually carry: an online PIN
         * block means [CVM_ONLINE_PIN], [EmvTransactionResult.hasPin] with no online PIN block
         * means an offline (on-card) PIN, otherwise no CVM was performed. See [CVM_ONLINE_PIN] for
         * which values are confirmed.
         */
        internal fun cvmCode(config: TransactionConfig, emvResult: EmvTransactionResult): Int = when {
            pinBlockToSend(config) != null -> CVM_ONLINE_PIN
            emvResult.hasPin -> CVM_OFFLINE_PIN
            else -> CVM_NONE
        }

        /**
         * The online PIN block for `encPinBlock` (sale envelope) and `pinBlock` (sale body) — only
         * when online PIN was actually entered for this sale. Offline PIN (verified by the card),
         * no CVM and signature have no PIN block, so the field is left out of the JSON entirely
         * (`null` fields aren't serialized), never sent empty.
         */
        internal fun pinBlockToSend(config: TransactionConfig): String? {
            val pinBlock = config.onlinePinBlock
            return if (pinBlock == null || pinBlock.trim().isEmpty()) null else pinBlock
        }

        /**
         * The RSA-wrapped PIN key for the exchange's `pinKey` — only alongside a PIN block it
         * encrypts, so never for offline PIN / no CVM / signature.
         */
        internal fun pinKeyToSend(config: TransactionConfig): String? =
            if (pinBlockToSend(config) != null) config.onlinePinKeyEncrypted else null

        internal fun pinEnterMode(config: TransactionConfig, emvResult: EmvTransactionResult): String =
            when (cvmCode(config, emvResult)) {
                CVM_ONLINE_PIN -> "ONLINE"
                CVM_OFFLINE_PIN -> "OFFLINE"
                else -> "NO_CVM"
            }

        /**
         * `cardType`: the issuer name in capitals. The old app's approved Mastercard chip sale
         * sent `"MASTERCARD"` with `extraData.issuerName` `"MasterCard"`.
         */
        internal fun cardType(emvResult: EmvTransactionResult): String? =
            emvResult.issuerName?.uppercase(Locale.US)

        /**
         * `dcc`: `"true"` for Mastercard, as the old app sent for its approved Mastercard chip
         * sale. The old app's list for the other schemes isn't known, so they stay `"false"`.
         */
        internal fun dcc(emvResult: EmvTransactionResult): String {
            val aid = emvResult.aid
            return if (aid != null && aid.uppercase(Locale.US).startsWith(MASTERCARD_RID)) "true" else "false"
        }

        /**
         * `extraData.cardType`. `"CHIP"` is confirmed by a real successful old-app chip sale; the
         * contactless/swipe/manual labels are still unconfirmed guesses.
         */
        private fun cardTypeLabel(mode: EntryMethod): String = when (mode) {
            EntryMethod.CHIP -> "CHIP"
            EntryMethod.CONTACTLESS -> "CLSS"
            EntryMethod.MAGSTRIPE -> "SWIPE"
            EntryMethod.MANUAL -> "MANUAL"
            EntryMethod.ANY -> "CHIP"
        }

        private fun toAuthResult(response: SaleResponse): AuthResult {
            val responseCode = response.responseCode
                // Fail closed: an answer without a response code is no approval.
                ?: return AuthResult.failed("Online failed: the host's answer has no response code")
            if (responseCode != 0) {
                return AuthResult.declined(responseCode.toString(), response.responseMessage ?: "Declined")
            }
            val authCode = response.authCode ?: ""
            val arpc = IssuerResponseFields.arpc(response.chipData)
            val issuerScript = IssuerResponseFields.issuerScript(response.chipData)
            return AuthResult.approved(authCode, "00", arpc, issuerScript)
        }
    }
}
