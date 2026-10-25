package com.emvenhance.network;

import com.emvenhance.network.model.AcknowledgementRequest;
import com.emvenhance.network.model.AcknowledgementResponse;
import com.emvenhance.network.model.GeneralRequest;
import com.emvenhance.network.model.GeneralResponse;
import com.emvenhance.network.model.PurchaseRequest;
import com.emvenhance.network.model.PurchaseResponse;
import com.emvenhance.network.model.ReverseRequest;

import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import okhttp3.ResponseBody;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface HostApiConnection {

    /** Online purchase authorization — see {@code EmvApiConnection#purchase} in the old project. */
    @POST("crypto/purchase")
    Single<PurchaseResponse> purchase(@Body PurchaseRequest request);

    /**
     * Establishes the encrypted-envelope session (TEK, and PEK if online PIN was collected) for a
     * transaction ahead of {@link #sale} — see {@code SaleCommunicationBehavior} and the old
     * project's {@code SaleActivity#callExchangeProcess}/{@code EmvApiConnection#saleInquiry}.
     *
     * <p>Path confirmed against the real {@code EmvApiConnection} interface (found in an uploaded
     * {@code model_layer.rar}) — {@code orchestration/exchange}, not {@code cacore/exchange} as
     * first guessed from {@code SaleActivity}'s field names alone; the first guess is what
     * produced a real 400 Bad Request in testing. {@code cacore/sale} does exist as a path, but
     * it's a separate, simpler test endpoint ({@code saleTest}/{@code SaleRequestTest}), not this one.
     */
    @POST("orchestration/exchange")
    Single<GeneralResponse> exchange(@HeaderMap Map<String, String> headers, @Body GeneralRequest request);

    /**
     * Online sale authorization over the encrypted {@code GeneralRequest}/{@code GeneralResponse}
     * envelope — see {@code SaleCommunicationBehavior} and the old project's
     * {@code SaleActivity#callSaleProcess}/{@code EmvApiConnection#sale}. Requires a prior
     * {@link #exchange} call for the same {@code asyncRequestId}. Path confirmed the same way as
     * {@link #exchange}'s — {@code orchestration/sale}, not {@code cacore/sale}.
     */
    @POST("orchestration/sale")
    Single<GeneralResponse> sale(@HeaderMap Map<String, String> headers, @Body GeneralRequest request);

    /**
     * Card-present refund over the same encrypted envelope as {@link #sale}, after an
     * {@link #exchange} with transaction type {@code "REFUND"} — the old project's
     * {@code EmvApiConnection#refund}. The payload is {@code RefundRequest}.
     */
    @POST("orchestration/refund")
    Single<GeneralResponse> refund(@HeaderMap Map<String, String> headers, @Body GeneralRequest request);

    /**
     * Undoes a sale or refund the host may have approved, named by its {@code asyncRequestId} —
     * the old project's {@code EmvApiConnection#reverse}. Plain JSON, no exchange or envelope;
     * {@code SaleCommunicationBehavior#reverse} sends it with the sale's headers.
     */
    @POST("orchestration/reverse")
    Single<GeneralResponse> reverse(@HeaderMap Map<String, String> headers, @Body ReverseRequest request);

    /**
     * Tells the host a sale or refund it approved is complete on the terminal, named by its
     * {@code asyncRequestId} — the old project's {@code EmvApiConnection#acknowledgement}. Plain
     * JSON, no exchange or envelope; {@code SaleCommunicationBehavior#acknowledge} sends it with
     * the sale's headers. (The old app's POS-as-ATM {@code atm/acknowledgement} waits for cash-in.)
     */
    @POST("orchestration/acknowledgement")
    Single<AcknowledgementResponse> acknowledgement(@HeaderMap Map<String, String> headers,
            @Body AcknowledgementRequest request);

    /**
     * Downloads this terminal's EMV/CLSS parameter package (a zip of {@code emv_param.emv} /
     * {@code clss_param.clss}) — see {@code EmvApiConnection#emvFileDownload} in the old project.
     * The raw bytes are handed to {@code EmvParamUpdater.applyFromZip} (bizentity module, since
     * parsing/DB-apply is PAX-specific) rather than parsed here, keeping this module vendor-agnostic.
     *
     * <p>{@code posType} is a fixed vendor literal ({@code "pax"} — confirmed by a real captured
     * old-app request), <b>not</b> the calling terminal's own model string — {@code EmvParamActivity}
     * used to pass {@code ModelInfo#getTerminalModel()} there (e.g. {@code "A920Pro"}), which the
     * host 500'd on.
     */
    @GET("foundation/tmsFileDownload/{posType}")
    Single<ResponseBody> emvFileDownload(@HeaderMap Map<String, String> headers,
            @Path("posType") String posType);
}
