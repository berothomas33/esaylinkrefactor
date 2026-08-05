package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

public final class OfflineDataAuthenticationBehavior extends BaseEmvStepBehavior {
    public OfflineDataAuthenticationBehavior() {
        super(EmvStep.OFFLINE_DATA_AUTHENTICATION);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        BehaviorResult cancel = cancelled(bridge);
        if (cancel != null) {
            return cancel;
        }
        String method = bridge.kernel().offlineDataAuthentication(context);
        if (method == null) {
            return fail("ODA_FAILED", "Offline data authentication failed", false);
        }
        context.put(EmvContext.KEY_ODA_METHOD, method);
        return BehaviorResult.success();
    }
}
