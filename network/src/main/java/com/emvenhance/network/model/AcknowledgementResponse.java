package com.emvenhance.network.model;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

/**
 * {@code orchestration/acknowledgement}'s answer — the old project's
 * {@code AcknowledgementResponse}: like {@link GeneralResponse}, but its {@code data} is a plain
 * string, not an envelope.
 */
public final class AcknowledgementResponse {

    @SerializedName("statusCode")
    private int statusCode;

    @SerializedName("message")
    @Nullable
    private String message;

    @SerializedName("data")
    @Nullable
    private String data;

    public int getStatusCode() {
        return statusCode;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public String getData() {
        return data;
    }
}
