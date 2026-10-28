package com.emvenhance.core.terminal;

import io.reactivex.rxjava3.subjects.PublishSubject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.transaction.TransactionType;
import com.emvenhance.core.emv.EmvEngine;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.transaction.TransactionStepEvent;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * WAITING_FOR_CARD carries KEY_RETRY_PROMPT only when the card has to be presented again, so the
 * search screen can show the reason on top of the card UI: a retry the kernel asked for (the
 * next search's prompt), or a read the cardholder can fix during the search.
 */
public class PosTerminalRetryPromptTest {

    private EmvEngine engine;
    private CapturingTerminal terminal;
    private final List<TransactionStepEvent> events = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws Exception {
        terminal = new CapturingTerminal();
        engine = terminal.behavior.engine();
        terminal.allSteps.subscribe(events::add);
        terminal.acceptCard(TransactionType.SALE, 1000);
        assertTrue("search never started", terminal.searches.tryAcquire(5, TimeUnit.SECONDS));
    }

    @After
    public void tearDown() {
        terminal.releases.release(10);
    }

    @Test
    public void firstSearchHasNoRetryPrompt() {
        TransactionStepEvent waiting = lastWaiting();
        assertEquals("Insert, tap, or swipe", waiting.getMessage());
        assertNull(waiting.get(TransactionStepEvent.KEY_RETRY_PROMPT));
    }

    @Test
    public void searchAfterARetryCarriesThePrompt() throws Exception {
        engine.requestRetry(config(), "See phone — verify on your phone, then tap again");

        terminal.releases.release(); // the first search returns: the kernel has returned
        assertTrue("no second search", terminal.searches.tryAcquire(5, TimeUnit.SECONDS));

        TransactionStepEvent waiting = lastWaiting();
        assertEquals("See phone — verify on your phone, then tap again", waiting.getMessage());
        assertEquals("See phone — verify on your phone, then tap again",
                waiting.get(TransactionStepEvent.KEY_RETRY_PROMPT));
    }

    @Test
    public void readerRetryDuringSearchCarriesThePrompt() {
        terminal.listener().onSearchRetry("Card not read — tap again and hold the card still");

        TransactionStepEvent waiting = lastWaiting();
        assertEquals("Card not read — tap again and hold the card still",
                waiting.get(TransactionStepEvent.KEY_RETRY_PROMPT));
    }

    private TransactionStepEvent lastWaiting() {
        TransactionStepEvent last = null;
        for (TransactionStepEvent e : events) {
            if (e.getStep() == TransactionStep.WAITING_FOR_CARD) {
                last = e;
            }
        }
        assertNotNull("no WAITING_FOR_CARD event", last);
        return last;
    }

    private static TransactionConfig config() {
        return new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.ANY);
    }

    /**
     * Each card search reports it started (as a vendor's does), hands the test its listener and
     * is held open until released.
     */
    private static final class CapturingTerminal extends PosTerminal {

        /** Every transaction's steps, for the test: each transaction has its own stream. */
        final PublishSubject<TransactionStepEvent> allSteps = PublishSubject.create();

        @Override
        public Transaction startTransaction(TransactionConfig config) {
            Transaction transaction = super.startTransaction(config);
            transaction.steps().subscribe(allSteps::onNext);
            return transaction;
        }

        /** One permit per search started. */
        final Semaphore searches;
        /** One permit lets one search return. */
        final Semaphore releases;
        private final java.util.concurrent.atomic.AtomicReference<CardSearchListener> captured;

        CapturingTerminal() {
            this(new Semaphore(0), new Semaphore(0),
                    new java.util.concurrent.atomic.AtomicReference<>());
        }

        private CapturingTerminal(Semaphore searches, Semaphore releases,
                java.util.concurrent.atomic.AtomicReference<CardSearchListener> captured) {
            super(behavior(searches, releases, captured), stub(CommunicationBehavior.class),
                    stub(PrinterBehavior.class));
            this.searches = searches;
            this.releases = releases;
            this.captured = captured;
        }

        /** The listener of the search running now. */
        CardSearchListener listener() {
            return captured.get();
        }

        private static AbstractEmvBehavior behavior(Semaphore searches, Semaphore releases,
                java.util.concurrent.atomic.AtomicReference<CardSearchListener> captured) {
            return StubBehavior.of((name, args) -> {
                if (name.equals("search")) {
                    CardSearchListener listener = (CardSearchListener) args[1];
                    captured.set(listener);
                    listener.onSearchStarted((TransactionConfig) args[0]);
                    searches.release();
                    releases.tryAcquire(5, TimeUnit.SECONDS);
                }
                return null;
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T stub(Class<T> type) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
        }

        private static Object defaultValue(Class<?> r) {
            if (r == boolean.class) {
                return false;
            }
            if (r.isPrimitive() && r != void.class) {
                return 0;
            }
            return null;
        }
    }
}
