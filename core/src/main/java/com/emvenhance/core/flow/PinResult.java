package com.emvenhance.core.flow;

import androidx.annotation.NonNull;

/** Outcome of a cardholder PIN prompt requested through {@link ServiceGateway#promptPin}. */
public final class PinResult {

    public enum Kind {
        ENTERED,
        BYPASSED,
        CANCELLED
    }

    private static final PinResult ENTERED = new PinResult(Kind.ENTERED);
    private static final PinResult BYPASSED = new PinResult(Kind.BYPASSED);
    private static final PinResult CANCELLED = new PinResult(Kind.CANCELLED);

    private final Kind kind;

    private PinResult(Kind kind) {
        this.kind = kind;
    }

    /**
     * PIN was captured. The block itself never leaves the PIN pad — the kernel reads it from
     * secure hardware, so nothing sensitive is carried in this object.
     */
    @NonNull
    public static PinResult entered() {
        return ENTERED;
    }

    @NonNull
    public static PinResult bypassed() {
        return BYPASSED;
    }

    @NonNull
    public static PinResult cancelled() {
        return CANCELLED;
    }

    @NonNull
    public Kind getKind() {
        return kind;
    }

    public boolean isEntered() {
        return kind == Kind.ENTERED;
    }

    public boolean isBypassed() {
        return kind == Kind.BYPASSED;
    }

    public boolean isCancelled() {
        return kind == Kind.CANCELLED;
    }
}
