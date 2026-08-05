package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

/**
 * Card search is owned by {@link com.emvenhance.core.terminal.PosTerminal}.
 * When the engine already has a card, this step skips.
 */
public final class SearchCardBehavior extends BaseEmvStepBehavior {
    public SearchCardBehavior() {
        super(EmvStep.SEARCH_CARD);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        if (context.getCard() != null) {
            return BehaviorResult.skip("Card already selected by PosTerminal");
        }
        return fail("NO_CARD", "No card present", true);
    }
}
