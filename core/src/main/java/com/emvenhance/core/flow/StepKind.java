package com.emvenhance.core.flow;

/**
 * Who actually executes an {@link com.emvenhance.core.event.EmvStep} for a given vendor.
 *
 * <p>This is the honest answer to "can this step own business logic?", and it varies by
 * vendor. A software kernel owns all steps; the PAX kernel runs most of them inside a
 * vendor binary and only surfaces a handful of decision points.
 */
public enum StepKind {

    /** We execute it. A behavior must be registered, and it drives the outcome. */
    OWNED,

    /**
     * The vendor kernel executes it, but calls back so a behavior can decide.
     * A behavior must be registered; its outcome is translated into the kernel's return value.
     */
    DELEGATED,

    /**
     * Nobody owns it — the vendor kernel performs it internally with no hook.
     * Emitted as a progress marker only. Registering a behavior for a SYNTHETIC step is a
     * configuration error and fails fast, because such a behavior would never run.
     */
    SYNTHETIC
}
