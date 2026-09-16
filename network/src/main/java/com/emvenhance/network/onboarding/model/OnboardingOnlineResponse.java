package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import androidx.annotation.Nullable;

/**
 * Decrypted shape of {@code foundation/onboarding/online}'s payload — one TMK block per
 * acquiring bank. The wire response is {@link com.emvenhance.network.model.GeneralResponse}
 * (encrypted); see {@code OnboardingClient#onboardOnline} for why decrypting into this shape
 * isn't wired up yet.
 */
public final class OnboardingOnlineResponse {

    @SerializedName("tmk_blocks")
    @Nullable
    private List<OnboardingOnlineModel> tmkBlocks;

    @Nullable
    public List<OnboardingOnlineModel> getTmkBlocks() {
        return tmkBlocks;
    }
}
