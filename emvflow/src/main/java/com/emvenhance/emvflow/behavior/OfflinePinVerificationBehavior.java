package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

public final class OfflinePinVerificationBehavior extends BaseEmvStepBehavior {
    public OfflinePinVerificationBehavior() {
        super(EmvStep.OFFLINE_PIN_VERIFICATION);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        return requireKernel(context, bridge, () -> bridge.kernel().verifyOfflinePin(context));
    }
}
