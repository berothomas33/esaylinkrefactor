package com.emvenhance.core.event

/**
 * Immutable event published on the engine's EMV steps (`EmvEngine#emvSteps`).
 *
 * Each emission represents one fine-grained EMV kernel phase that has just started or
 * completed. The optional `detail` carries extra information the kernel provides for that
 * particular step (e.g. "DDA" for the ODA step, or "contactless" for application selection).
 *
 * These events are *not* replayed to late collectors; if the UI attaches after step 5, it will
 * only see steps 6+.
 */
class EmvStepEvent(val step: EmvStep, val detail: String?) {

    override fun toString(): String =
        step.number.toString() + ". " + step.title + (if (detail != null) " ($detail)" else "")
}
