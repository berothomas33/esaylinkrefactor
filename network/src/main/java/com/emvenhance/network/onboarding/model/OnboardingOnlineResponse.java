package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import androidx.annotation.Nullable;

public final class OnboardingOnlineResponse {

    @SerializedName("tmk_blocks")
    @Nullable
    private List<OnboardingOnlineModel> tmkBlocks;

    @Nullable
    public List<OnboardingOnlineModel> getTmkBlocks() {
        return tmkBlocks;
    }
}
