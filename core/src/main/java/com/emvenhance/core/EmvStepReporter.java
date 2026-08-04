package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * Receives ordered EMV kernel steps as the vendor adapter advances through the flow.
 *
 * <p>Used by progress helpers (e.g. gap-filling step reporters) so they do not depend on
 * a vendor-specific callback type. The PAX adapter forwards each report into
 * {@link EmvEngine#notifyEmvStep}, which routes through {@code dispatchEmvStep}.
 */
public interface EmvStepReporter {

    void onStep(EmvStep step, @Nullable String detail);
}
