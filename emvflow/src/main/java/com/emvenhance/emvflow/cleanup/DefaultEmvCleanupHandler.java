package com.emvenhance.emvflow.cleanup;

import com.emvenhance.core.engine.EmvCleanupHandler;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.port.EmvKernelPort;

/** Clears sensitive context and aborts the kernel session. */
public final class DefaultEmvCleanupHandler implements EmvCleanupHandler {

    private final EmvKernelPort kernelPort;

    public DefaultEmvCleanupHandler(EmvKernelPort kernelPort) {
        this.kernelPort = kernelPort;
    }

    @Override
    public void cleanup(EmvContext context) {
        if (kernelPort != null) {
            kernelPort.abort();
        }
        // Drop PIN-related flags; leave audit fields for logging if needed
        context.put(EmvContext.KEY_PIN_ONLINE, null);
        context.put(EmvContext.KEY_PIN_BYPASS, null);
    }
}
