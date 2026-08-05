package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.behavior.EmvError;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.port.EmvSignal;
import com.emvenhance.core.port.InteractionRequest;
import java.util.List;

public final class WaitApplicationSelectionBehavior extends BaseEmvStepBehavior {
    public WaitApplicationSelectionBehavior() {
        super(EmvStep.WAIT_APPLICATION_SELECTION);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        List<String> candidates = context.getCandidates();
        if (candidates.size() <= 1) {
            return BehaviorResult.skip("Single candidate");
        }
        // Auto-select first when interaction port is silent (Fake demo).
        // Still emit WaitInteraction so UI ports can override via resume.
        bridge.requestInteraction(InteractionRequest.selectApplication(candidates));
        // Immediate auto-pick for headless / Fake Auto port:
        context.put(EmvContext.KEY_SELECTED_AID, candidates.get(0));
        return BehaviorResult.success();
    }

    @Override
    public BehaviorResult onSignal(EmvContext context, BehaviorBridge bridge, EmvSignal signal) {
        if (signal instanceof EmvSignal.ApplicationSelected) {
            context.put(EmvContext.KEY_SELECTED_AID,
                    ((EmvSignal.ApplicationSelected) signal).getAid());
            return BehaviorResult.success();
        }
        if (signal instanceof EmvSignal.UserCancel) {
            return BehaviorResult.failure(EmvError.cancelled(step()));
        }
        return super.onSignal(context, bridge, signal);
    }
}
