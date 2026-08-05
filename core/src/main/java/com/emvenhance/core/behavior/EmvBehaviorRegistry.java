package com.emvenhance.core.behavior;

import androidx.annotation.Nullable;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

/**
 * Maps {@link EmvStep} → {@link EmvStepBehavior}. Implementations live in {@code :emvflow}.
 */
public interface EmvBehaviorRegistry {

    void register(EmvStep step, EmvStepBehavior behavior);

    @Nullable
    EmvStepBehavior resolve(EmvStep step, EmvContext context);
}
