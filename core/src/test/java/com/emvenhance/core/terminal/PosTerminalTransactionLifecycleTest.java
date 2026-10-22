package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * One transaction at a time: a start while the previous one hasn't finished — even cancelled, with
 * its search still stopping — is refused as "terminal busy" at once, never queued; a transaction
 * cancelled before it ran still frees the terminal. And a search that
 * outlives its transaction can't fail, or start EMV for, the next one.
 */
public class PosTerminalTransactionLifecycleTest {

    private TestTerminal terminal;
    private final List<TransactionStepEvent> events = new CopyOnWriteArrayList<>();
    private final List<Long> emvStartedWithAmount = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() {
        terminal = new TestTerminal();
        terminal.transactionSteps().subscribe(events::add);
    }

    @After
    public void tearDown() {
        terminal.releaseAll();
    }

    @Test
    public void lateCallbacksFromAnEarlierSearchDoNotEndTheNextTransaction() throws Exception {
        Search first = cancelledSearchThenNextTransaction();

        first.listener.onSearchCancelled();
        first.listener.onSearchTimeout();
        first.listener.onReaderError("PICC: reader error");
        first.listener.onSearchStarted(first.config);

        assertTrue("next transaction got " + events, events.isEmpty());
        assertTrue(terminal.engine.isRunning());
    }

    @Test
    public void aCardCaughtByAnEarlierSearchNeverStartsEmv() throws Exception {
        Search first = cancelledSearchThenNextTransaction();
        Search second = terminal.nextSearch();

        first.listener.onChipDetected(CardPresence.chip());
        assertTrue(emvStartedWithAmount.isEmpty());
        assertFalse(hasStep(TransactionStep.CARD_DETECTED));

        second.listener.onChipDetected(CardPresence.chip());
        assertEquals(Collections.singletonList(2000L), emvStartedWithAmount);
    }

    @Test
    public void aStartWhileTheCancelledSearchIsStillStoppingIsRefusedAsBusy() throws Exception {
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        terminal.cancelTransaction();
        events.clear();

        assertFalse("busy", starts(2000));
        assertTrue("busy is not reported on the old transaction's steps: " + events,
                events.isEmpty());

        first.release.countDown(); // the old search finally stops
        assertTrue("old search kept searching", awaitTrue(() -> first.cancelledWhenReleased));
        Search second = startWhenFree(3000);
        assertEquals(3000L, second.config.getAmountMinor());
        assertSame("one transaction thread", first.thread, second.thread);
        assertNull("the refused start never ran", terminal.searches.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    public void aStartWhileSearchingIsRefusedAndTheFirstCarriesOn() throws Exception {
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        events.clear();

        assertFalse("busy", starts(2000));

        assertNull(terminal.searches.poll(300, TimeUnit.MILLISECONDS));
        assertTrue("first transaction got " + events, events.isEmpty());
        assertTrue(terminal.engine.isRunning());
        first.listener.onChipDetected(CardPresence.chip());
        assertEquals(Collections.singletonList(1000L), emvStartedWithAmount);
    }

    @Test
    public void aTransactionCancelledBeforeItRanStillFreesTheTerminal() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        transactionExecutor().execute(() -> {
            try {
                hold.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        terminal.acceptCard(TransactionType.SALE, 1000);
        terminal.cancelTransaction(); // its work is still queued behind the held thread

        hold.countDown();

        Search next = startWhenFree(2000);
        assertEquals("the cancelled one never searched", 2000L, next.config.getAmountMinor());
    }

    @Test
    public void aCancelEndsTheTransactionOnceAndNothingAfterChangesThatEnd() throws Exception {
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search search = terminal.nextSearch();
        events.clear();

        terminal.cancelTransaction();
        terminal.cancelTransaction(); // a second cancel does nothing
        terminal.engine.notifyCompleted(); // nor does an end reported after it

        assertEquals(TransactionStep.ERROR, events.get(0).getStep());
        assertEquals("Transaction cancelled", events.get(0).getMessage());
        assertEquals("one cancel ERROR: " + events, 1, count(TransactionStep.ERROR));
        assertFalse(terminal.engine.isRunning());
        assertTrue("still ended by the cancel", terminal.engine.isCancelled());
        search.release.countDown();
    }

    @Test
    public void theFirstEndEndsTheTransactionAndALaterCancelDoesNothing() throws Exception {
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search search = terminal.nextSearch();
        events.clear();

        terminal.engine.notifyError("Reader error");
        terminal.cancelTransaction();

        assertEquals("no cancel ERROR after the end: " + events, 1, count(TransactionStep.ERROR));
        assertFalse(terminal.engine.isRunning());
        assertFalse("ended by the error, not the cancel", terminal.engine.isCancelled());
        search.release.countDown();
    }

    /**
     * A transaction for 1000, cancelled, whose listener is still held (like a vendor reporting
     * late, from its own thread), then the next transaction for 2000, searching.
     */
    private Search cancelledSearchThenNextTransaction() throws InterruptedException {
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        terminal.cancelTransaction();
        first.release.countDown();
        Search second = startWhenFree(2000);
        terminal.searches.add(second); // hand it back for tests that need it
        events.clear();
        return first;
    }

    /** Starts a sale as soon as the terminal stops being busy, and returns its search. */
    private Search startWhenFree(long amount) throws InterruptedException {
        assertTrue("terminal stayed busy",
                awaitTrue(() -> starts(amount)));
        return terminal.nextSearch();
    }

    private Executor transactionExecutor() throws ReflectiveOperationException {
        Field field = PosTerminal.class.getDeclaredField("transactionExecutor");
        field.setAccessible(true);
        return (Executor) field.get(terminal);
    }

    /** Starts a sale; {@code false}: refused as busy, at once, on this thread. */
    private boolean starts(long amount) {
        try {
            terminal.acceptCard(TransactionType.SALE, amount);
            return true;
        } catch (TerminalBusyException busy) {
            return false;
        }
    }

    private static boolean awaitTrue(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private int count(TransactionStep step) {
        int n = 0;
        for (TransactionStepEvent e : events) {
            if (e.getStep() == step) {
                n++;
            }
        }
        return n;
    }

    private boolean hasStep(TransactionStep step) {
        for (TransactionStepEvent e : events) {
            if (e.getStep() == step) {
                return true;
            }
        }
        return false;
    }

    /** One {@code searchCard} call, held open until the test releases it. */
    private static final class Search {
        final TransactionConfig config;
        final CardSearchListener listener;
        final CountDownLatch release = new CountDownLatch(1);
        final Thread thread = Thread.currentThread();
        volatile boolean cancelledWhenReleased;

        Search(TransactionConfig config, CardSearchListener listener) {
            this.config = config;
            this.listener = listener;
        }
    }

    private final class TestTerminal extends PosTerminal {

        final LinkedBlockingQueue<Search> searches = new LinkedBlockingQueue<>();
        private final List<Search> all = new CopyOnWriteArrayList<>();

        TestTerminal() {
            super(behavior(), stub(CommunicationBehavior.class), stub(PrinterBehavior.class));
        }

        Search nextSearch() throws InterruptedException {
            Search search = searches.poll(5, TimeUnit.SECONDS);
            assertNotNull("search never started", search);
            return search;
        }

        void releaseAll() {
            for (Search search : all) {
                search.release.countDown();
            }
        }

        /** Like a vendor poll that stops only when released, and reports nothing on its own. */
        @Override
        public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
            Search search = new Search(config, listener);
            all.add(search);
            searches.add(search);
            try {
                search.release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            search.cancelledWhenReleased = isSearchCancelled();
            return null;
        }

        @Override
        protected void cancelCardSearch() {
        }
    }

    /** prepare() succeeds; start() records the amount EMV ran for. */
    private EmvBehavior behavior() {
        return (EmvBehavior) Proxy.newProxyInstance(EmvBehavior.class.getClassLoader(),
                new Class<?>[] {EmvBehavior.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepare")) {
                        return Boolean.TRUE;
                    }
                    if (method.getName().equals("start")) {
                        emvStartedWithAmount.add(((TransactionConfig) args[1]).getAmountMinor());
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
    }
}
