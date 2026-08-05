package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

public final class ReadApplicationDataBehavior extends BaseEmvStepBehavior {
    public ReadApplicationDataBehavior() {
        super(EmvStep.READ_APPLICATION_DATA);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        BehaviorResult result = requireKernel(context, bridge,
                () -> bridge.kernel().readApplicationData(context));
        if (!result.isSuccess()) {
            return result;
        }
        // Ensure cardholder data defaults for UI if kernel did not set them
        if (context.getString(EmvContext.KEY_PAN) == null) {
            CardPresence card = context.getCard();
            if (card != null && card.isMagstripe() && card.getTrack2() != null) {
                context.put(EmvContext.KEY_PAN, card.getTrack2().split("[=D]")[0]);
            } else if (card != null && card.isManual() && card.getManualPan() != null) {
                context.put(EmvContext.KEY_PAN, card.getManualPan());
            }
        }
        return BehaviorResult.success();
    }
}
