package com.emvenhance.emvflow.behavior;

import com.emvenhance.core.behavior.EmvBehaviorRegistry;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.emvflow.registry.DefaultEmvBehaviorRegistry;

/**
 * Registers the standard set of EMV step behaviors on a registry.
 */
public final class StandardEmvBehaviors {

    private StandardEmvBehaviors() {
    }

    public static EmvBehaviorRegistry createRegistry() {
        DefaultEmvBehaviorRegistry registry = new DefaultEmvBehaviorRegistry();
        registerAll(registry);
        return registry;
    }

    public static void registerAll(EmvBehaviorRegistry registry) {
        registry.register(EmvStep.TERMINAL_INITIALIZATION, new TerminalInitializationBehavior());
        registry.register(EmvStep.SEARCH_CARD, new SearchCardBehavior());
        registry.register(EmvStep.APPLICATION_SELECTION, new ApplicationSelectionBehavior());
        registry.register(EmvStep.WAIT_APPLICATION_SELECTION, new WaitApplicationSelectionBehavior());
        registry.register(EmvStep.FINAL_APPLICATION_SELECTION, new FinalApplicationSelectionBehavior());
        registry.register(EmvStep.READ_APPLICATION_DATA, new ReadApplicationDataBehavior());
        registry.register(EmvStep.SET_TRANSACTION_DATA, new SetTransactionDataBehavior());
        registry.register(EmvStep.OFFLINE_DATA_AUTHENTICATION, new OfflineDataAuthenticationBehavior());
        registry.register(EmvStep.PROCESS_RESTRICTIONS, new ProcessRestrictionsBehavior());
        registry.register(EmvStep.CARDHOLDER_VERIFICATION, new CardholderVerificationBehavior());
        registry.register(EmvStep.OFFLINE_PIN_VERIFICATION, new OfflinePinVerificationBehavior());
        registry.register(EmvStep.TERMINAL_RISK_MANAGEMENT, new TerminalRiskManagementBehavior());
        registry.register(EmvStep.TERMINAL_ACTION_ANALYSIS, new TerminalActionAnalysisBehavior());
        registry.register(EmvStep.START_ONLINE_PROCESS, new StartOnlineProcessBehavior());
        registry.register(EmvStep.ISSUER_AUTHENTICATION, new IssuerAuthenticationBehavior());
        registry.register(EmvStep.SCRIPT_PROCESSING, new ScriptProcessingBehavior());
        registry.register(EmvStep.TRANSACTION_COMPLETION, new TransactionCompletionBehavior());
    }
}
