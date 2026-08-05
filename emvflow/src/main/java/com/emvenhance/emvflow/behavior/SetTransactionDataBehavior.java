package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

public final class SetTransactionDataBehavior extends BaseEmvStepBehavior {
    public SetTransactionDataBehavior() {
        super(EmvStep.SET_TRANSACTION_DATA);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        return requireKernel(context, bridge, () -> bridge.kernel().setTransactionData(context));
    }
}
