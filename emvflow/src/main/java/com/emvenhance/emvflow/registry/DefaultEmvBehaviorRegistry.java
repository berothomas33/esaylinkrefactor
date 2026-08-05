package com.emvenhance.emvflow.registry;

import androidx.annotation.Nullable;
import com.emvenhance.core.behavior.EmvBehaviorRegistry;
import com.emvenhance.core.behavior.EmvStepBehavior;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;
import java.util.EnumMap;
import java.util.Map;

/** Default {@link EmvStep} → {@link EmvStepBehavior} map. */
public final class DefaultEmvBehaviorRegistry implements EmvBehaviorRegistry {

    private final Map<EmvStep, EmvStepBehavior> behaviors = new EnumMap<>(EmvStep.class);

    @Override
    public void register(EmvStep step, EmvStepBehavior behavior) {
        behaviors.put(step, behavior);
    }

    @Nullable
    @Override
    public EmvStepBehavior resolve(EmvStep step, EmvContext context) {
        return behaviors.get(step);
    }

    public boolean isEmpty() {
        return behaviors.isEmpty();
    }
}
