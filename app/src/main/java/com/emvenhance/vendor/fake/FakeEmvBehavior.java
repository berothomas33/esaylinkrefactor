package com.emvenhance.vendor.fake;

import com.emvenhance.core.behavior.EmvBehaviorRegistry;
import com.emvenhance.core.behavior.EmvTransitionPolicy;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.port.EmvInteractionPort;
import com.emvenhance.core.terminal.EmvBehavior;
import com.emvenhance.emvflow.behavior.StandardEmvBehaviors;
import com.emvenhance.emvflow.cleanup.DefaultEmvCleanupHandler;
import com.emvenhance.emvflow.policy.DefaultEmvTransitionPolicy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fake vendor wiring for the behavior-driven EMV engine.
 *
 * <p>No phase logic lives here — only registry / kernel / policy attachment and
 * {@link EmvEngine#prepareFlow} / {@link EmvEngine#runFlow}.
 */
public class FakeEmvBehavior implements EmvBehavior {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final FakeKernelPort kernelPort = new FakeKernelPort();
    private final EmvBehaviorRegistry registry = StandardEmvBehaviors.createRegistry();
    private final EmvTransitionPolicy policy = new DefaultEmvTransitionPolicy();
    private final EmvInteractionPort interactionPort = new EmvInteractionPort.Auto();

    private boolean flowAttached;

    @Override
    public boolean prepare(EmvEngine engine, TransactionConfig config) {
        cancelled.set(false);
        kernelPort.reset();
        attachFlowIfNeeded(engine);
        return engine.prepareFlow(config);
    }

    @Override
    public void start(EmvEngine engine, TransactionConfig config, CardPresence card) {
        cancelled.set(false);
        if (cancelled.get()) {
            return;
        }
        attachFlowIfNeeded(engine);
        engine.runFlow(config, card);
    }

    @Override
    public void cancel() {
        cancelled.set(true);
        kernelPort.abort();
    }

    private void attachFlowIfNeeded(EmvEngine engine) {
        if (flowAttached) {
            return;
        }
        engine.attachFlow(
                registry,
                policy,
                kernelPort,
                interactionPort,
                new DefaultEmvCleanupHandler(kernelPort));
        flowAttached = true;
    }
}
