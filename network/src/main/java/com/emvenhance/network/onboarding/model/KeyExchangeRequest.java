package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

/** RKI pilot key exchange: the KEK, RSA-wrapped with the host's public key, hex. */
public final class KeyExchangeRequest {

    @SerializedName("encryptedKek")
    private final String encryptedKek;

    public KeyExchangeRequest(String encryptedKek) {
        this.encryptedKek = encryptedKek;
    }
}
