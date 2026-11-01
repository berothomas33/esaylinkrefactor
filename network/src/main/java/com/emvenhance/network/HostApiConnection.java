package com.emvenhance.network;

import com.emvenhance.network.model.AcknowledgementRequest;
import com.emvenhance.network.model.AcknowledgementResponse;
import com.emvenhance.network.model.GeneralRequest;
import com.emvenhance.network.model.GeneralResponse;
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

    @POST("orchestration/exchange")
    Single<GeneralResponse> exchange(@HeaderMap Map<String, String> headers, @Body GeneralRequest request);

    @POST("orchestration/sale")
    Single<GeneralResponse> sale(@HeaderMap Map<String, String> headers, @Body GeneralRequest request);

    @POST("orchestration/refund")
    Single<GeneralResponse> refund(@HeaderMap Map<String, String> headers, @Body GeneralRequest request);

    @POST("orchestration/reverse")
    Single<GeneralResponse> reverse(@HeaderMap Map<String, String> headers, @Body ReverseRequest request);

    @POST("orchestration/acknowledgement")
    Single<AcknowledgementResponse> acknowledgement(@HeaderMap Map<String, String> headers,
            @Body AcknowledgementRequest request);

    @GET("foundation/tmsFileDownload/{posType}")
    Single<ResponseBody> emvFileDownload(@HeaderMap Map<String, String> headers,
            @Path("posType") String posType);
}
