package com.emvenhance.network;

import com.emvenhance.network.model.GeneralRequest;
import com.emvenhance.network.model.GeneralResponse;
import com.emvenhance.network.model.PurchaseRequest;
import com.emvenhance.network.model.PurchaseResponse;

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
     * project's {@code SaleActivity#callExchangeProcess}/{@code EmvApiConnection#exchange}.
     */
    @POST("cacore/exchange")
    Single<GeneralResponse> exchange(@HeaderMap Map<String, String> headers, @Body GeneralRequest request);

    /**
     * Online sale authorization over the encrypted {@code GeneralRequest}/{@code GeneralResponse}
     * envelope — see {@code SaleCommunicationBehavior} and the old project's
     * {@code SaleActivity#callSaleProcess}/{@code EmvApiConnection#sale}. Requires a prior
     * {@link #exchange} call for the same {@code asyncRequestId}.
     */
    @POST("cacore/sale")
    Single<GeneralResponse> sale(@HeaderMap Map<String, String> headers, @Body GeneralRequest request);

    /**
     * Downloads this terminal's EMV/CLSS parameter package (a zip of {@code emv_param.emv} /
     * {@code clss_param.clss}) — see {@code EmvApiConnection#emvFileDownload} in the old project.
     * The raw bytes are handed to {@code EmvParamUpdater.applyFromZip} (bizentity module, since
     * parsing/DB-apply is PAX-specific) rather than parsed here, keeping this module vendor-agnostic.
     */
    @GET("foundation/tmsFileDownload/{posType}")
    Single<ResponseBody> emvFileDownload(@HeaderMap Map<String, String> headers,
            @Path("posType") String posType);
}
