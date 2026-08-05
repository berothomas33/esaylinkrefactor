package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import io.reactivex.rxjava3.core.Single;

/**
 * Captures a cardholder PIN. Supplied by the vendor layer, because the PIN pad is hardware.
 *
 * <p>Returning a {@link Single} is what lets a PIN prompt be genuinely asynchronous without
 * any latch: the behavior awaiting it simply completes when the cardholder finishes.
 */
@FunctionalInterface
public interface PinPrompter {

    /** Never returns the PIN block itself — it stays inside secure hardware. */
    @NonNull
    Single<PinResult> prompt(boolean onlinePin, int triesLeft);

    /** Always bypasses. Useful for demo terminals and for magstripe/manual flows. */
    @NonNull
    static PinPrompter alwaysBypass() {
        return (onlinePin, triesLeft) -> Single.just(PinResult.bypassed());
    }
}
