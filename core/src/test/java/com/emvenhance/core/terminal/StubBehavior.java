package com.emvenhance.core.terminal;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.transaction.TransactionConfig;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * An {@link AbstractEmvBehavior} stub for terminal tests: keeps the engine {@code attach} gives
 * it and returns it from {@code engine()}, as every behavior does; {@code prepare}, {@code start}
 * and {@code dispose} go to {@code calls} (as {@link EmvBehavior}'s methods, with this stub as
 * the "proxy"), and it runs no EMV step.
 */
final class StubBehavior extends AbstractEmvBehavior {

    private final InvocationHandler calls;

    private StubBehavior(InvocationHandler calls) {
        this.calls = calls;
    }

    static AbstractEmvBehavior of(InvocationHandler calls) {
        return new StubBehavior(calls);
    }

    @Override
    public boolean prepare(TransactionConfig config, PosTerminal.Transaction transaction) {
        return Boolean.TRUE.equals(call("prepare", config, transaction));
    }

    @Override
    public void start(TransactionConfig config, CardPresence card,
            PosTerminal.Transaction transaction) {
        call("start", config, card, transaction);
    }

    @Override
    public void dispose() {
        call("dispose");
    }

    private Object call(String name, Object... args) {
        try {
            for (Method method : EmvBehavior.class.getMethods()) {
                if (method.getName().equals(name)) {
                    return calls.invoke(this, method, args.length == 0 ? null : args);
                }
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        throw new AssertionError("EmvBehavior has no " + name);
    }

    // ─── No EMV behind a stub ─────────────────────────────────────────────

    @Override protected byte[] readIccTag(int tag) { return null; }
    @Override protected void onTerminalInitialization(TransactionConfig c) { }
    @Override protected void onApplicationSelection(TransactionConfig c, CardPresence p) { }
    @Override protected void onWaitApplicationSelection(TransactionConfig c, CardPresence p) { }
    @Override protected void onFinalApplicationSelection(TransactionConfig c, CardPresence p) { }
    @Override protected void onReadApplicationData(TransactionConfig c, CardPresence p) { }
    @Override protected void onSetTransactionData(TransactionConfig c, CardPresence p) { }
    @Override protected void onOfflineDataAuthentication(TransactionConfig c, CardPresence p) { }
    @Override protected void onProcessRestrictions(TransactionConfig c, CardPresence p) { }
    @Override protected void onCardholderVerification(TransactionConfig c, CardPresence p) { }
    @Override protected void onOfflinePinVerification(TransactionConfig c, CardPresence p) { }
    @Override protected void onTerminalRiskManagement(TransactionConfig c, CardPresence p) { }
    @Override protected void onTerminalActionAnalysis(TransactionConfig c, CardPresence p) { }
    @Override protected void onStartOnlineProcess(TransactionConfig c, CardPresence p) { }
    @Override protected void onIssuerAuthentication(TransactionConfig c, CardPresence p) { }
    @Override protected void onScriptProcessing(TransactionConfig c, CardPresence p) { }
    @Override protected void onTransactionCompletion(TransactionConfig c, CardPresence p) { }
}
