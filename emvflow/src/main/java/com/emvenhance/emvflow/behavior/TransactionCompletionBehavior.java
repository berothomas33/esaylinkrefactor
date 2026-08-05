package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.host.AuthResult;

public final class TransactionCompletionBehavior extends BaseEmvStepBehavior {
    public TransactionCompletionBehavior() {
        super(EmvStep.TRANSACTION_COMPLETION);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        BehaviorResult cancel = cancelled(bridge);
        if (cancel != null) {
            return cancel;
        }
        if (!bridge.kernel().completeTransaction(context)) {
            return fail("COMPLETION_FAILED", "Transaction completion failed", false);
        }
        // Finalize approve/decline flags if not already set
        if (context.get(EmvContext.KEY_APPROVED) == null) {
            AuthResult host = context.getHostResult();
            if (host != null) {
                context.put(EmvContext.KEY_APPROVED, host.isApproved());
                context.put(EmvContext.KEY_RESULT_MESSAGE,
                        host.isApproved() ? "ONLINE APPROVED" : host.getMessage());
            } else if (context.getBoolean(EmvContext.KEY_OFFLINE_APPROVED, false)) {
                context.put(EmvContext.KEY_APPROVED, true);
                context.put(EmvContext.KEY_RESULT_MESSAGE, "OFFLINE APPROVED");
            } else {
                context.put(EmvContext.KEY_APPROVED, false);
                context.put(EmvContext.KEY_RESULT_MESSAGE, "DECLINED");
            }
        }
        return BehaviorResult.success();
    }
}
