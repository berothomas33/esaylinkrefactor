package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.transaction.TransactionConfig;

final class StubBehavior extends AbstractEmvBehavior {

    interface Calls {
        @Nullable
        Object call(String name, Object... args) throws Exception;
    }

    private final Calls calls;
    private volatile PosTerminal.Transaction transaction;

    private StubBehavior(Calls calls) {
        this.calls = calls;
    }

    static AbstractEmvBehavior of(Calls calls) {
        return new StubBehavior(calls);
    }

    @Override
    public void run(TransactionConfig config, PosTerminal.Transaction transaction) {
        this.transaction = transaction;
        super.run(config, transaction);
    }

    boolean searchCancelled() {
        return !isRunning();
    }

    @Override
    protected void onTerminalInitialization(TransactionConfig config) {
        if (Boolean.FALSE.equals(call("initialize", config))) {
            finishError("Terminal initialization failed");
        }
    }

    @Override
    protected void onSearchCard(TransactionConfig config, @Nullable CardPresence card) {
        call("search", config, cardSearchListener(), this);
    }

    @Override
    protected void onApplicationSelection(TransactionConfig config, CardPresence card) {
        call("start", config, card, transaction);
    }

    @Override
    protected void onReadApplicationData(TransactionConfig config, CardPresence card) {
        call("start", config, card, transaction);
    }

    @Override
    public void cancelCardSearch() {
        call("cancelCardSearch");
    }

    @Override
    public void dispose() {
        call("dispose");
    }

    @Nullable
    private Object call(String name, Object... args) {
        try {
            return calls.call(name, args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override protected byte[] readIccTag(int tag) { return null; }
    @Override protected void onWaitApplicationSelection(TransactionConfig c, CardPresence p) { }
    @Override protected void onFinalApplicationSelection(TransactionConfig c, CardPresence p) { }
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
