package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

public final class TerminalActionAnalysisBehavior extends BaseEmvStepBehavior {
    public TerminalActionAnalysisBehavior() {
        super(EmvStep.TERMINAL_ACTION_ANALYSIS);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        BehaviorResult cancel = cancelled(bridge);
        if (cancel != null) {
            return cancel;
        }
        String decision = bridge.kernel().terminalActionAnalysis(context);
        if (decision == null) {
            return fail("TAA_FAILED", "Terminal action analysis failed", false);
        }
        switch (decision) {
            case "ONLINE":
                context.put(EmvContext.KEY_ONLINE_REQUIRED, true);
                break;
            case "APPROVE":
                context.put(EmvContext.KEY_ONLINE_REQUIRED, false);
                context.put(EmvContext.KEY_OFFLINE_APPROVED, true);
                context.put(EmvContext.KEY_APPROVED, true);
                context.put(EmvContext.KEY_RESULT_MESSAGE, "OFFLINE APPROVED");
                break;
            case "DECLINE":
                context.put(EmvContext.KEY_ONLINE_REQUIRED, false);
                context.put(EmvContext.KEY_APPROVED, false);
                context.put(EmvContext.KEY_RESULT_MESSAGE, "OFFLINE DECLINED");
                break;
            default:
                return fail("TAA_UNKNOWN", "Unknown TAA decision: " + decision, false);
        }
        return BehaviorResult.success();
    }
}
