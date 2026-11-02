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

final class TestSession {

    final EmvEngine engine;
    private final AbstractEmvBehavior behavior;
    private final Terminal terminal;
    private final List<TransactionStepEvent> published = new CopyOnWriteArrayList<>();
    private PosTerminal.Transaction transaction;

    TestSession(AbstractEmvBehavior behavior, AbstractEmvBehavior.Authorizer authorizer) {
        this.behavior = behavior;
        this.terminal = new Terminal(behavior, authorizer);
        this.engine = behavior.engine();
    }

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

    void start(CardPresence card) {
        behavior.start(card);
    }

    void attempt(TransactionConfig config, CardPresence card) {
        prepare(config);
        start(card);
    }

    void cancel() {
        transaction.cancel();
    }

    PosTerminal.Transaction transaction() {
        return transaction;
    }

    List<TransactionStepEvent> steps() {
        List<TransactionStepEvent> steps = new ArrayList<>(published);
        TransactionStepEvent end = endOf(transaction);
        if (end != null) {
            steps.add(end);
        }
        return steps;
    }

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

    private static final class Terminal extends PosTerminal {

        private final AbstractEmvBehavior.Authorizer authorizer;

        Terminal(AbstractEmvBehavior behavior, AbstractEmvBehavior.Authorizer authorizer) {
            super(behavior, null, null);
            this.authorizer = authorizer;
        }

        @Override
        public AuthResult authorize(TransactionConfig config) {
            return authorizer.authorize(config);
        }
    }
}
