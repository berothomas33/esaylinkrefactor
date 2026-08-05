package com.emvenhance.emvflow.policy;

import androidx.annotation.Nullable;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.behavior.EmvTransitionPolicy;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.TransactionStep;

/**
 * Default contact / contactless / mag / manual transition graphs.
 * Behaviors write facts on {@link EmvContext}; this class alone chooses the next step.
 */
public final class DefaultEmvTransitionPolicy implements EmvTransitionPolicy {

    @Nullable
    @Override
    public EmvStep next(EmvStep from, BehaviorResult result, EmvContext context) {
        if (result instanceof BehaviorResult.Success) {
            EmvStep suggested = ((BehaviorResult.Success) result).getSuggestedNext();
            if (suggested != null) {
                return suggested;
            }
        }

        EntryMethod entry = context.getEntryMethod();

        switch (from) {
            case TERMINAL_INITIALIZATION:
                return EmvStep.SEARCH_CARD;

            case SEARCH_CARD:
                return firstAfterSearch(entry);

            case APPLICATION_SELECTION:
                if (context.getCandidates().size() <= 1) {
                    return EmvStep.FINAL_APPLICATION_SELECTION;
                }
                return EmvStep.WAIT_APPLICATION_SELECTION;

            case WAIT_APPLICATION_SELECTION:
                return EmvStep.FINAL_APPLICATION_SELECTION;

            case FINAL_APPLICATION_SELECTION:
                return EmvStep.READ_APPLICATION_DATA;

            case READ_APPLICATION_DATA:
                if (entry == EntryMethod.CONTACTLESS) {
                    // Demo/Fake abbreviated CLSS: offline complete after read
                    context.put(EmvContext.KEY_ONLINE_REQUIRED, false);
                    context.put(EmvContext.KEY_OFFLINE_APPROVED, true);
                    context.put(EmvContext.KEY_APPROVED, true);
                    context.put(EmvContext.KEY_RESULT_MESSAGE, "OFFLINE APPROVED");
                    context.put(EmvContext.KEY_EMIT_ISSUER_STEPS, false);
                    return EmvStep.TRANSACTION_COMPLETION;
                }
                if (entry == EntryMethod.MAGSTRIPE || entry == EntryMethod.MANUAL) {
                    context.put(EmvContext.KEY_ONLINE_REQUIRED, true);
                    context.put(EmvContext.KEY_EMIT_ISSUER_STEPS, false);
                    return EmvStep.START_ONLINE_PROCESS;
                }
                return EmvStep.SET_TRANSACTION_DATA;

            case SET_TRANSACTION_DATA:
                return EmvStep.OFFLINE_DATA_AUTHENTICATION;

            case OFFLINE_DATA_AUTHENTICATION:
                return EmvStep.PROCESS_RESTRICTIONS;

            case PROCESS_RESTRICTIONS:
                return EmvStep.CARDHOLDER_VERIFICATION;

            case CARDHOLDER_VERIFICATION: {
                String cvm = context.getString(EmvContext.KEY_CVM_METHOD);
                if ("OFFLINE_PIN".equals(cvm)) {
                    return EmvStep.OFFLINE_PIN_VERIFICATION;
                }
                return EmvStep.TERMINAL_RISK_MANAGEMENT;
            }

            case OFFLINE_PIN_VERIFICATION:
                return EmvStep.TERMINAL_RISK_MANAGEMENT;

            case TERMINAL_RISK_MANAGEMENT:
                return EmvStep.TERMINAL_ACTION_ANALYSIS;

            case TERMINAL_ACTION_ANALYSIS:
                if (context.getBoolean(EmvContext.KEY_ONLINE_REQUIRED, true)) {
                    context.put(EmvContext.KEY_EMIT_ISSUER_STEPS, true);
                    return EmvStep.START_ONLINE_PROCESS;
                }
                return EmvStep.TRANSACTION_COMPLETION;

            case START_ONLINE_PROCESS:
                if (context.getBoolean(EmvContext.KEY_EMIT_ISSUER_STEPS, false)) {
                    return EmvStep.ISSUER_AUTHENTICATION;
                }
                return EmvStep.TRANSACTION_COMPLETION;

            case ISSUER_AUTHENTICATION:
                return EmvStep.SCRIPT_PROCESSING;

            case SCRIPT_PROCESSING:
                return EmvStep.TRANSACTION_COMPLETION;

            case TRANSACTION_COMPLETION:
                return null;

            default:
                return null;
        }
    }

    private static EmvStep firstAfterSearch(@Nullable EntryMethod entry) {
        if (entry == EntryMethod.MAGSTRIPE || entry == EntryMethod.MANUAL) {
            return EmvStep.READ_APPLICATION_DATA;
        }
        return EmvStep.APPLICATION_SELECTION;
    }

    @Nullable
    @Override
    public TransactionStep mapToTransactionStep(EmvStep emvStep, EmvContext context) {
        switch (emvStep) {
            case TERMINAL_INITIALIZATION:
                return null; // emitted explicitly in EmvEngine.prepareFlow
            case FINAL_APPLICATION_SELECTION:
                return TransactionStep.APPLICATION_SELECTED;
            case READ_APPLICATION_DATA:
                return TransactionStep.CARD_READ;
            case OFFLINE_PIN_VERIFICATION:
            case CARDHOLDER_VERIFICATION:
                // Emit CARDHOLDER_VERIFIED after CVM path completes — policy maps
                // when leaving offline PIN or when CVM is not offline PIN at risk mgmt.
                return null;
            case TERMINAL_RISK_MANAGEMENT:
                return TransactionStep.CARDHOLDER_VERIFIED;
            default:
                return null;
        }
    }
}
