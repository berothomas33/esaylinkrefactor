package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

public final class FinalApplicationSelectionBehavior extends BaseEmvStepBehavior {
    public FinalApplicationSelectionBehavior() {
        super(EmvStep.FINAL_APPLICATION_SELECTION);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        String aid = context.getString(EmvContext.KEY_SELECTED_AID);
        if (aid == null && !context.getCandidates().isEmpty()) {
            aid = context.getCandidates().get(0);
            context.put(EmvContext.KEY_SELECTED_AID, aid);
        }
        if (aid == null) {
            return fail("NO_AID", "No application selected", false);
        }
        final String selected = aid;
        return requireKernel(context, bridge, () -> bridge.kernel().finalSelect(context, selected));
    }
}
