package com.emvenhance.core.engine;

/**
 * Releases kernel / sensitive resources after hard failure or cancel.
 */
public interface EmvCleanupHandler {

    void cleanup(EmvContext context);

    EmvCleanupHandler NO_OP = context -> {
    };
}
