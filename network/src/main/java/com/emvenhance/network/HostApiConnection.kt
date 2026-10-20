package com.emvenhance.network

import com.emvenhance.network.model.GeneralRequest
import com.emvenhance.network.model.GeneralResponse
import com.emvenhance.network.model.PurchaseRequest
import com.emvenhance.network.model.PurchaseResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The host's endpoints as Retrofit `suspend` functions (Retrofit 2.9 supports them natively):
 * a non-2xx answer throws `HttpException`, a network failure its `IOException`, and cancelling
 * the calling coroutine (the terminal's online timeout) cancels the HTTP call.
 */
interface HostApiConnection {

    /** Online purchase authorization — see `EmvApiConnection#purchase` in the old project. */
    @POST("crypto/purchase")
    suspend fun purchase(@Body request: PurchaseRequest): PurchaseResponse

    /**
     * Establishes the encrypted-envelope session (TEK, and PEK if online PIN was collected) for a
     * transaction ahead of [sale] — see `SaleCommunicationBehavior` and the old project's
     * `SaleActivity#callExchangeProcess`/`EmvApiConnection#saleInquiry`.
     *
     * Path confirmed against the real `EmvApiConnection` interface (found in an uploaded
     * `model_layer.rar`) — `orchestration/exchange`, not `cacore/exchange` as first guessed from
     * `SaleActivity`'s field names alone; the first guess is what produced a real 400 Bad Request
     * in testing. `cacore/sale` does exist as a path, but it's a separate, simpler test endpoint
     * (`saleTest`/`SaleRequestTest`), not this one.
     */
    @POST("orchestration/exchange")
    suspend fun exchange(
        @HeaderMap headers: Map<String, String>,
        @Body request: GeneralRequest,
    ): GeneralResponse

    /**
     * Online sale authorization over the encrypted `GeneralRequest`/`GeneralResponse` envelope —
     * see `SaleCommunicationBehavior` and the old project's
     * `SaleActivity#callSaleProcess`/`EmvApiConnection#sale`. Requires a prior [exchange] call for
     * the same `asyncRequestId`. Path confirmed the same way as [exchange]'s —
     * `orchestration/sale`, not `cacore/sale`.
     */
    @POST("orchestration/sale")
    suspend fun sale(
        @HeaderMap headers: Map<String, String>,
        @Body request: GeneralRequest,
    ): GeneralResponse

    /**
     * Card-present refund over the same encrypted envelope as [sale], after an [exchange] with
     * transaction type `"REFUND"` — the old project's `EmvApiConnection#refund`. The payload is
     * `RefundRequest`.
     */
    @POST("orchestration/refund")
    suspend fun refund(
        @HeaderMap headers: Map<String, String>,
        @Body request: GeneralRequest,
    ): GeneralResponse

    /**
     * Downloads this terminal's EMV/CLSS parameter package (a zip of `emv_param.emv` /
     * `clss_param.clss`) — see `EmvApiConnection#emvFileDownload` in the old project. The raw
     * bytes are handed to `EmvParamUpdater.applyFromZip` (bizentity module, since parsing/DB-apply
     * is PAX-specific) rather than parsed here, keeping this module vendor-agnostic.
     *
     * `posType` is a fixed vendor literal (`"pax"` — confirmed by a real captured old-app
     * request), **not** the calling terminal's own model string — `EmvParamActivity` used to pass
     * `ModelInfo#getTerminalModel()` there (e.g. `"A920Pro"`), which the host 500'd on.
     */
    @GET("foundation/tmsFileDownload/{posType}")
    suspend fun emvFileDownload(
        @HeaderMap headers: Map<String, String>,
        @Path("posType") posType: String,
    ): ResponseBody
}
