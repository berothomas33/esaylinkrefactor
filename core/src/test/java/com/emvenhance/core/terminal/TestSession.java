package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.emv.EmvEngine;
import com.emvenhance.core.transaction.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for the transaction thread ({@code onTransactionStarted}, the behavior's run loop)
 * when a test drives a behavior on its own, on the test's thread: a real
 * {@link PosTerminal.Transaction}, handed to the behavior as an attempt hands it, and the card a
 * search would have found. Unlike the terminal, the end is listed at once in {@link #steps()}
 * (there is no transaction thread to free first).
 */
final class TestSession {

    final EmvEngine engine;
    private final AbstractEmvBehavior behavior;
    private final Terminal terminal;
    private final List<TransactionStepEvent> published = new CopyOnWriteArrayList<>();
    private PosTerminal.Transaction transaction;

    TestSession(AbstractEmvBehavior behavior, EmvEngine.Authorizer authorizer) {
        this.behavior = behavior;
        this.terminal = new Terminal(behavior, authorizer);
        this.engine = behavior.engine();
    }

    /** A new transaction, as {@code onTransactionStarted} begins one. */
    PosTerminal.Transaction begin(TransactionConfig config) {
        published.clear();
        transaction = newTransaction(config);
        transaction.steps().subscribe(published::add);
        engine.begin();
        return transaction;
    }

    boolean prepare(TransactionConfig config) {
        return behavior.prepare(config, transaction);
    }

    /** The card a search found: the EMV on it, with the prepared config. */
    void start(CardPresence card) {
        behavior.start(card);
    }

    /** One attempt as the run loop takes it: {@link #prepare}, then the card {@link #start}. */
    void attempt(TransactionConfig config, CardPresence card) {
        prepare(config);
        start(card);
    }

    /** As the screen's Cancel: {@link PosTerminal.Transaction#cancel()}. */
    void cancel() {
        transaction.cancel();
    }

    PosTerminal.Transaction transaction() {
        return transaction;
    }

    /** Every step the transaction reported, its end included once it has ended. */
    List<TransactionStepEvent> steps() {
        List<TransactionStepEvent> steps = new ArrayList<>(published);
        TransactionStepEvent end = endOf(transaction);
        if (end != null) {
            steps.add(end);
        }
        return steps;
    }

    // The Transaction is the terminal's: made only by startTransaction, its end kept private.

    private PosTerminal.Transaction newTransaction(TransactionConfig config) {
        try {
            Constructor<PosTerminal.Transaction> constructor = PosTerminal.Transaction.class
                    .getDeclaredConstructor(PosTerminal.class, TransactionConfig.class);
            constructor.setAccessible(true);
            return constructor.newInstance(terminal, config);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Nullable
    private static TransactionStepEvent endOf(PosTerminal.Transaction transaction) {
        try {
            Field end = PosTerminal.Transaction.class.getDeclaredField("end");
            end.setAccessible(true);
            return (TransactionStepEvent) end.get(transaction);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Goes online through the test's authorizer. */
    private static final class Terminal extends PosTerminal {

        private final EmvEngine.Authorizer authorizer;

        Terminal(AbstractEmvBehavior behavior, EmvEngine.Authorizer authorizer) {
            super(behavior, null, null);
            this.authorizer = authorizer;
        }

        @Override
        public AuthResult authorize(TransactionConfig config) {
            return authorizer.authorize(config);
        }
    }
}
