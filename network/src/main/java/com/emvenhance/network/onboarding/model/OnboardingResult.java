package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * The data of both onboarding answers: offline onboarding (publicKey, signature) and RKI
 * onboarding (serviceAccount under the APP_TDK, TDK / TPK as TR-31 with KCVs, signature).
 */
public final class OnboardingResult {

    @SerializedName("serviceAccount")
    @Nullable
    private String serviceAccount;

    @SerializedName("publicKey")
    @Nullable
    private String publicKey;

    @SerializedName("transactionKey")
    @Nullable
    private String transactionKey;

    @SerializedName("transactionKeyKcv")
    @Nullable
    private String transactionKeyKcv;

    @SerializedName("pinKey")
    @Nullable
    private String pinKey;

    @SerializedName("pinKeyKcv")
    @Nullable
    private String pinKeyKcv;

    @SerializedName("signature")
    @Nullable
    private String signature;

    @Nullable
    public String getServiceAccount() {
        return serviceAccount;
    }

    @Nullable
    public String getPublicKey() {
        return publicKey;
    }

    /** The TDK as a TR-31 key block under the KBPK. */
    @Nullable
    public String getTransactionKey() {
        return transactionKey;
    }

    @Nullable
    public String getTransactionKeyKcv() {
        return transactionKeyKcv;
    }

    /** The TPK as a TR-31 key block under the KBPK. */
    @Nullable
    public String getPinKey() {
        return pinKey;
    }

    @Nullable
    public String getPinKeyKcv() {
        return pinKeyKcv;
    }

    @Nullable
    public String getSignature() {
        return signature;
    }
}
