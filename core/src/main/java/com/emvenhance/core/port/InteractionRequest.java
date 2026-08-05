package com.emvenhance.core.port;

import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * UI / cardholder interaction requested by a waiting behavior.
 */
public final class InteractionRequest {

    public enum Type {
        SELECT_APPLICATION,
        ENTER_PIN,
        CONFIRM_AMOUNT,
        DISPLAY_MESSAGE,
        SEE_PHONE,
        CONFIRM_CARD
    }

    private final Type type;
    @Nullable
    private final String message;
    private final List<String> candidates;
    private final Map<String, Object> extras;

    public InteractionRequest(Type type, @Nullable String message,
            @Nullable List<String> candidates, @Nullable Map<String, Object> extras) {
        this.type = type;
        this.message = message;
        this.candidates = candidates != null ? candidates : Collections.emptyList();
        this.extras = extras != null ? extras : Collections.emptyMap();
    }

    public static InteractionRequest selectApplication(List<String> candidates) {
        return new InteractionRequest(Type.SELECT_APPLICATION, "Select application",
                candidates, null);
    }

    public static InteractionRequest enterPin(String pinType) {
        return new InteractionRequest(Type.ENTER_PIN, "Enter PIN", null,
                Collections.singletonMap("pinType", pinType));
    }

    public static InteractionRequest displayMessage(String message) {
        return new InteractionRequest(Type.DISPLAY_MESSAGE, message, null, null);
    }

    public Type getType() {
        return type;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public List<String> getCandidates() {
        return candidates;
    }

    public Map<String, Object> getExtras() {
        return extras;
    }
}
