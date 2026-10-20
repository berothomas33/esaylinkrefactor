package com.emvenhance.network

import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.host.CommunicationBehavior
import com.emvenhance.network.model.PurchaseRequest
import com.emvenhance.network.model.PurchaseResponse
import com.emvenhance.network.tlv.BerTlv
import com.emvenhance.network.tlv.IssuerResponseFields

/**
 * Real [CommunicationBehavior] — calls the host's online purchase endpoint and maps the response
 * into [AuthResult], following the old project's `EmvApiClient#purchase` behavior. Replaces
 * `HostDefaults.declineUntilWired()` once a vendor terminal is ready to go online for real.
 *
 * The response's EMV tags (ARPC / issuer scripts) arrive bundled inside one `chipData` hex
 * BER-TLV blob rather than as separate JSON fields — same shape the old project used. See
 * [BerTlv]/[IssuerResponseFields] for the extraction and the caveat on the exact tag set an
 * issuer bundles there.
 */
class RetrofitCommunicationBehavior(private val connection: HostApiConnection) : CommunicationBehavior {

    constructor() : this(HostApiClient.create())

    override suspend fun authorize(config: TransactionConfig): AuthResult {
        val request = PurchaseRequest(
            config.amountMinor,
            config.procCode,
            PosEntryModeCodes.forEntryMethod(config.mode),
            config.iccData,
            config.pan,
            config.onlinePinBlock,
            config.onlinePinKeyEncrypted,
        )
        return toAuthResult(connection.purchase(request))
    }

    private companion object {
        fun toAuthResult(response: PurchaseResponse): AuthResult {
            val code = response.responseCode
            val responseCode = if (code != null) String.format("%02d", code) else "96"

            val approved = "00" == responseCode
            if (!approved) {
                return AuthResult.declined(responseCode, response.responseMessage ?: "Declined")
            }

            val chipData = response.chipData
            val arpc = IssuerResponseFields.arpc(chipData)
            val issuerScript = IssuerResponseFields.issuerScript(chipData)

            return AuthResult.approved(response.authCode ?: "", responseCode, arpc, issuerScript)
        }
    }
}
