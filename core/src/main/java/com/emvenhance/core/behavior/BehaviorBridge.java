package com.emvenhance.core.behavior;

import androidx.annotation.Nullable;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.port.EmvKernelPort;
import com.emvenhance.core.port.InteractionRequest;

/**
 * Narrow facade the engine gives each {@link EmvStepBehavior}. Prevents behaviors from
 * depending on the full engine API.
 */
public interface BehaviorBridge {

    void notifyEmvStep(EmvStep step, @Nullable String detail);

    void requestInteraction(InteractionRequest request);

    EmvKernelPort kernel();

    AuthResult authorize();

    boolean isCancelled();
}
