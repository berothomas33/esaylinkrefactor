package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;
import java.util.ArrayList;
import java.util.List;

public final class ApplicationSelectionBehavior extends BaseEmvStepBehavior {
    public ApplicationSelectionBehavior() {
        super(EmvStep.APPLICATION_SELECTION);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        BehaviorResult cancel = cancelled(bridge);
        if (cancel != null) {
            return cancel;
        }
        List<String> candidates = bridge.kernel().buildCandidates(context);
        if (candidates == null || candidates.isEmpty()) {
            return fail("NO_CANDIDATES", "No applications found", false);
        }
        context.put(EmvContext.KEY_CANDIDATES, new ArrayList<>(candidates));
        if (candidates.size() == 1) {
            context.put(EmvContext.KEY_SELECTED_AID, candidates.get(0));
        }
        return BehaviorResult.success();
    }
}
