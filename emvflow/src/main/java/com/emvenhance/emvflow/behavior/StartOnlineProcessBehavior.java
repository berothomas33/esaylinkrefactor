package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.host.AuthResult;

public final class StartOnlineProcessBehavior extends BaseEmvStepBehavior {
    public StartOnlineProcessBehavior() {
        super(EmvStep.START_ONLINE_PROCESS);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        BehaviorResult cancel = cancelled(bridge);
        if (cancel != null) {
            return cancel;
        }
        if (!bridge.kernel().startOnlineProcess(context)) {
            return fail("ONLINE_PREP_FAILED", "Failed to start online process", false);
        }
        AuthResult auth = bridge.authorize();
        context.setHostResult(auth);
        context.put(EmvContext.KEY_APPROVED, auth.isApproved());
        context.put(EmvContext.KEY_RESULT_MESSAGE,
                auth.isApproved() ? "ONLINE APPROVED" : auth.getMessage());
        if (!auth.isApproved()) {
            // Still continue to completion / issuer scripts as EMV requires on some paths;
            // policy may skip issuer steps when emitIssuerSteps is false.
            return BehaviorResult.success();
        }
        return BehaviorResult.success();
    }
}
