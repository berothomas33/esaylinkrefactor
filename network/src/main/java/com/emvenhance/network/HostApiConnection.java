package com.emvenhance.network;

import com.emvenhance.network.model.PurchaseRequest;
import com.emvenhance.network.model.PurchaseResponse;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.POST;

/** Online purchase authorization — see {@code EmvApiConnection#purchase} in the old project. */
public interface HostApiConnection {

    @POST("crypto/purchase")
    Single<PurchaseResponse> purchase(@Body PurchaseRequest request);
}
