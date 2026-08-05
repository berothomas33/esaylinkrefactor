package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

public final class IssuerAuthenticationBehavior extends BaseEmvStepBehavior {
    public IssuerAuthenticationBehavior() {
        super(EmvStep.ISSUER_AUTHENTICATION);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        if (!context.getBoolean(EmvContext.KEY_EMIT_ISSUER_STEPS, false)) {
            return BehaviorResult.skip("No issuer authentication data");
        }
        return requireKernel(context, bridge, () -> bridge.kernel().issuerAuthentication(context));
    }
}
