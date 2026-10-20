package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/** The encrypted envelope carried by [GeneralResponse.data]. */
class DataModel {

    @SerializedName("encSerializedResponse")
    var encSerializedResponse: String? = null
        private set

    @SerializedName("transactionType")
    var transactionType: String? = null
        private set

    @SerializedName("asyncRequestId")
    var asyncRequestId: String? = null
        private set
}
