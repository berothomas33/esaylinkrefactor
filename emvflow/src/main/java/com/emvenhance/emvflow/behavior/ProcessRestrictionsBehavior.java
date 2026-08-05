package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

public final class ProcessRestrictionsBehavior extends BaseEmvStepBehavior {
    public ProcessRestrictionsBehavior() {
        super(EmvStep.PROCESS_RESTRICTIONS);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        return requireKernel(context, bridge, () -> bridge.kernel().processRestrictions(context));
    }
}
