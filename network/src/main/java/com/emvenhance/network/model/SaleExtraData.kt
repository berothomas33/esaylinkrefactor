package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/**
 * Serialized (as JSON, into [SaleRequest.extraData]) supplementary EMV data — mirrors the old
 * project's `SaleExtraData`, reconstructed from its setter calls in `SaleActivity#getSaleRequest`.
 */
class SaleExtraData(
    @field:SerializedName("aid") val aid: String,
    @field:SerializedName("appName") val appName: String,
    @field:SerializedName("issuerName") val issuerName: String?,
    @field:SerializedName("pinEnterMode") val pinEnterMode: String,
    @field:SerializedName("cardHolderName") val cardHolderName: String?,
    @field:SerializedName("cardType") val cardType: String,
)
