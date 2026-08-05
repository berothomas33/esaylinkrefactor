package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

public final class TerminalRiskManagementBehavior extends BaseEmvStepBehavior {
    public TerminalRiskManagementBehavior() {
        super(EmvStep.TERMINAL_RISK_MANAGEMENT);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        return requireKernel(context, bridge,
                () -> bridge.kernel().terminalRiskManagement(context));
    }
}
