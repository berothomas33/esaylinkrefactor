/**
 * Behavior-per-step EMV flow: one class per EMV phase, a declarative {@link
 * com.emvenhance.core.flow.FlowPlan} instead of a switch, and async-native execution.
 *
 * <pre>
 *   PosTerminal
 *     └── EmvFlowOrchestrator (resolve → guard → execute → retry → emit → interpret)
 *           ├── StepRegistry   Map&lt;EmvStep, EmvStepBehavior&gt;
 *           ├── FlowPlan       successor edges + StepKind + RetryPolicy  (data)
 *           └── StepDriver     who holds control: our loop, or a vendor kernel
 * </pre>
 *
 * <p>{@link com.emvenhance.core.flow.StepKind} records the fact that ownership varies by
 * vendor: a software kernel owns every step, while the PAX kernel executes most of them
 * inside a vendor binary and surfaces only a handful of callbacks.
 */
package com.emvenhance.core.flow;
