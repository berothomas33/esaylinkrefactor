package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

public final class TerminalInitializationBehavior extends BaseEmvStepBehavior {
    public TerminalInitializationBehavior() {
        super(EmvStep.TERMINAL_INITIALIZATION);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        return requireKernel(context, bridge, () -> bridge.kernel().preTrans(context));
    }
}
