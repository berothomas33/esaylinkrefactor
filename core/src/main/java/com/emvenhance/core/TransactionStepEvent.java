package com.emvenhance.core;

import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable event emitted on the transaction-steps {@code BehaviorSubject}.
 *
 * <p>Every emission is a new instance carrying the new {@link TransactionStep} plus an
 * optional bag of contextual data — PAN, issuer name, authorization result, error reason,
 * etc.  Using a {@code Map} rather than individual fields keeps this class stable when new
 * data needs arise: the vendor implementation just puts a new key, and only the consumer
 * that cares about that key reads it.
 *
 * <p>Because the subject is a {@code BehaviorSubject}, a late subscriber (or one reattaching
 * after rotation) immediately receives the most recent event, which is exactly the behaviour
 * the UI needs.
 */
public final class TransactionStepEvent {

    // ─── Well-known data keys ────────────────────────────────────────────
    public static final String KEY_PAN              = "pan";
    public static final String KEY_ISSUER_NAME      = "issuerName";
    public static final String KEY_CARDHOLDER_NAME  = "cardHolderName";
    public static final String KEY_MODE             = "mode";
    public static final String KEY_AMOUNT           = "amount";
    public static final String KEY_RESULT           = "result";
    public static final String KEY_ERROR            = "error";
    public static final String KEY_ONLINE_PIN       = "onlinePin";
    public static final String KEY_PIN_BYPASS       = "pinBypassAllowed";
    public static final String KEY_PIN_TRIES_LEFT   = "pinTriesLeft";

    private final TransactionStep step;
    private final String message;
    private final Map<String, Object> data;

    private TransactionStepEvent(TransactionStep step, @Nullable String message,
            Map<String, Object> data) {
        this.step = step;
        this.message = message;
        this.data = Collections.unmodifiableMap(data);
    }

    // ─── Factory methods ─────────────────────────────────────────────────

    public static TransactionStepEvent idle() {
        return new TransactionStepEvent(TransactionStep.IDLE, null, Collections.emptyMap());
    }

    public static TransactionStepEvent of(TransactionStep step) {
        return new TransactionStepEvent(step, step.getLabel(), Collections.emptyMap());
    }

    public static TransactionStepEvent of(TransactionStep step, @Nullable String message) {
        return new TransactionStepEvent(step, message, Collections.emptyMap());
    }

    public static TransactionStepEvent of(TransactionStep step, @Nullable String message,
            Map<String, Object> data) {
        return new TransactionStepEvent(step, message, new HashMap<>(data));
    }

    // ─── Builder for multi-field events ──────────────────────────────────

    public static Builder builder(TransactionStep step) {
        return new Builder(step);
    }

    public static final class Builder {
        private final TransactionStep step;
        private String message;
        private final Map<String, Object> data = new HashMap<>();

        private Builder(TransactionStep step) {
            this.step = step;
            this.message = step.getLabel();
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder put(String key, Object value) {
            data.put(key, value);
            return this;
        }

        public TransactionStepEvent build() {
            return new TransactionStepEvent(step, message, data);
        }
    }

    // ─── Accessors ───────────────────────────────────────────────────────

    public TransactionStep getStep() {
        return step;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    /** Type-safe convenience for reading a data entry. */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    /** String convenience — returns the value or a dash when absent. */
    public String getString(String key) {
        Object v = data.get(key);
        return v != null ? v.toString() : "—";
    }

    @Override
    public String toString() {
        return step + (message != null ? ": " + message : "")
                + (data.isEmpty() ? "" : " " + data);
    }
}
