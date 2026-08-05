package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.behavior.EmvError;
import com.emvenhance.core.behavior.EmvStepBehavior;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;

/** Shared helpers for step behaviors. */
abstract class BaseEmvStepBehavior implements EmvStepBehavior {

    private final EmvStep step;

    protected BaseEmvStepBehavior(EmvStep step) {
        this.step = step;
    }

    @Override
    public final EmvStep step() {
        return step;
    }

    protected BehaviorResult fail(String code, String message, boolean retryable) {
        return BehaviorResult.failure(EmvError.of(code, message, step, retryable));
    }

    protected BehaviorResult cancelled(BehaviorBridge bridge) {
        if (bridge.isCancelled()) {
            return BehaviorResult.failure(EmvError.cancelled(step));
        }
        return null;
    }

    protected BehaviorResult requireKernel(EmvContext context, BehaviorBridge bridge,
            KernelOp op) {
        BehaviorResult cancel = cancelled(bridge);
        if (cancel != null) {
            return cancel;
        }
        try {
            if (!op.run()) {
                return fail("KERNEL_" + step.name(), step.getTitle() + " failed", false);
            }
            return BehaviorResult.success();
        } catch (Exception e) {
            return BehaviorResult.failure(new EmvError(
                    "KERNEL_EXCEPTION",
                    e.getMessage() != null ? e.getMessage() : step.getTitle() + " failed",
                    step,
                    false,
                    e));
        }
    }

    @FunctionalInterface
    protected interface KernelOp {
        boolean run();
    }
}
