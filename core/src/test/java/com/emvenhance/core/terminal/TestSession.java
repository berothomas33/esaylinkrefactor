package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for {@code Transaction.run} when a test drives a behavior on its own, on the test's
 * thread: a real {@link PosTerminal.Transaction} of a terminal that never searches, handed to the
 * behavior as {@code run} hands it. Unlike {@code run}, the end is listed at once in
 * {@link #steps()} (there is no transaction thread to free first).
 */
final class TestSession {

    final EmvEngine engine;
    private final EmvBehavior behavior;
    private final Terminal terminal;
    private final List<TransactionStepEvent> published = new CopyOnWriteArrayList<>();
    private PosTerminal.Transaction transaction;

    TestSession(EmvBehavior behavior, EmvEngine.Authorizer authorizer) {
        this.behavior = behavior;
        this.terminal = new Terminal(behavior, authorizer);
        this.engine = behavior.engine();
    }

    /** A new transaction, as {@code Transaction.run} begins one. */
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

    void start(TransactionConfig config, CardPresence card) {
        behavior.start(config, card, transaction);
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

    /** Never searches; goes online through the test's authorizer. */
    private static final class Terminal extends PosTerminal {

        private final EmvEngine.Authorizer authorizer;

        Terminal(EmvBehavior behavior, EmvEngine.Authorizer authorizer) {
            super(behavior, null, null);
            this.authorizer = authorizer;
        }

        @Override
        public AuthResult authorize(TransactionConfig config) {
            return authorizer.authorize(config);
        }

        @Override
        public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
            return null;
        }

        @Override
        protected void cancelCardSearch() {
        }
    }
}
