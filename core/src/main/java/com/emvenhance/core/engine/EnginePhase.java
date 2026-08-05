package com.emvenhance.core.engine;

/**
 * Internal lifecycle of {@link EmvEngine}, independent of {@link com.emvenhance.core.event.EmvStep}.
 */
public enum EnginePhase {
    IDLE,
    RUNNING,
    WAITING,
    COMPLETED,
    FAILED,
    CANCELLED
}
