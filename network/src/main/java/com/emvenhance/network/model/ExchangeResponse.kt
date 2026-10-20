package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/**
 * Decrypted payload of the exchange call's response — the old project's real
 * `ExchngeResponseModel` (sic) class turned up in an uploaded `model_layer.rar` (older snapshot
 * than `SaleActivity.java`, but this class matches what `SaleActivity#callExchangeProcess` reads
 * from it: `onUs`, `bankIndex`, `pinKey` — a bank-issued TPK, used there to save it — that step
 * is out of scope here, see `SaleCommunicationBehavior`).
 */
class ExchangeResponse {

    @SerializedName("onUs")
    var isOnUs: Boolean = false
        private set

    @SerializedName("bankIndex")
    var bankIndex: Int = 0
        private set

    @SerializedName("pinKey")
    var pinKey: String? = null
        private set
}
