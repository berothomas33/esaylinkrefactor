package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.behavior.EmvError;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.port.EmvSignal;

public final class CardholderVerificationBehavior extends BaseEmvStepBehavior {
    public CardholderVerificationBehavior() {
        super(EmvStep.CARDHOLDER_VERIFICATION);
    }

    @Override
    public BehaviorResult execute(EmvContext context, BehaviorBridge bridge) {
        BehaviorResult cancel = cancelled(bridge);
        if (cancel != null) {
            return cancel;
        }
        String cvm = bridge.kernel().processCvm(context);
        if (cvm == null || cvm.isEmpty()) {
            return fail("CVM_FAILED", "Cardholder verification failed", false);
        }
        context.put(EmvContext.KEY_CVM_METHOD, cvm);
        if ("ONLINE_PIN".equals(cvm)) {
            context.put(EmvContext.KEY_PIN_ONLINE, true);
        }
        if ("OFFLINE_PIN".equals(cvm) || "ONLINE_PIN".equals(cvm)) {
            context.put(EmvContext.KEY_PIN_BYPASS, true);
            context.put(EmvContext.KEY_PIN_TRIES, 3);
        }
        return BehaviorResult.success();
    }

    @Override
    public BehaviorResult onSignal(EmvContext context, BehaviorBridge bridge, EmvSignal signal) {
        if (signal instanceof EmvSignal.PinEntered) {
            EmvSignal.PinEntered pin = (EmvSignal.PinEntered) signal;
            context.put(EmvContext.KEY_PIN_ONLINE, pin.isOnlinePin());
            context.put(EmvContext.KEY_PIN_BYPASS, pin.isBypass());
            context.put(EmvContext.KEY_PIN_TRIES, pin.getTriesLeft());
            return BehaviorResult.success();
        }
        if (signal instanceof EmvSignal.PinCancelled) {
            return BehaviorResult.failure(EmvError.cancelled(step()));
        }
        return super.onSignal(context, bridge, signal);
    }
}
