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
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.observers.TestObserver;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * One transaction at a time: a start while the previous one hasn't finished — even cancelled, with
 * its search still stopping — is refused as "terminal busy", never queued. And a search that
 * outlives its transaction can't fail, or start EMV for, the next one.
 *
 * <p>Each transaction is a {@code Completable}: subscribe starts it, dispose cancels that one only,
 * and {@code onComplete} comes once the terminal is free.
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
        TestObserver<Void> sale = terminal.acceptCard(TransactionType.SALE, 1000).test();
        Search first = terminal.nextSearch();
        sale.dispose();
        events.clear();

        terminal.acceptCard(TransactionType.SALE, 2000).test()
                .assertError(TerminalBusyException.class);
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
        terminal.acceptCard(TransactionType.SALE, 1000).test();
        Search first = terminal.nextSearch();
        events.clear();

        TestObserver<Void> refused = terminal.acceptCard(TransactionType.SALE, 2000).test();
        refused.assertError(TerminalBusyException.class);
        refused.dispose(); // leaving the refused screen cancels nothing

        assertNull(terminal.searches.poll(300, TimeUnit.MILLISECONDS));
        assertTrue("first transaction got " + events, events.isEmpty());
        assertTrue(terminal.engine.isRunning());
        first.listener.onChipDetected(CardPresence.chip());
        assertEquals(Collections.singletonList(1000L), emvStartedWithAmount);
    }

    @Test
    public void disposeCancelsItsOwnTransaction() throws Exception {
        TestObserver<Void> sale = terminal.acceptCard(TransactionType.SALE, 1000).test();
        Search first = terminal.nextSearch();

        sale.dispose();

        assertTrue(terminal.isSearchCancelled());
        assertEquals(1, terminal.searchCancels.get());
        assertTrue("the cancel reached the engine", hasStep(TransactionStep.ERROR));
        first.release.countDown();
        assertTrue("old search kept searching", awaitTrue(() -> first.cancelledWhenReleased));
    }

    @Test
    public void aTransactionDisposedBeforeItRanStillFreesTheTerminal() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        transactionScheduler().scheduleDirect(() -> {
            try {
                hold.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        TestObserver<Void> sale = terminal.acceptCard(TransactionType.SALE, 1000).test();
        sale.dispose(); // its work is still queued behind the held thread

        hold.countDown();

        Search next = startWhenFree(2000);
        assertEquals("the disposed one never searched", 2000L, next.config.getAmountMinor());
    }

    @Test
    public void completesOnlyOnceTheTerminalIsFree() throws Exception {
        TestObserver<Void> sale = terminal.acceptCard(TransactionType.SALE, 1000).test();
        Search first = terminal.nextSearch();
        sale.assertNotComplete();

        first.listener.onSearchTimeout(); // like a vendor: one callback, then the search returns
        first.release.countDown();

        sale.await(5, TimeUnit.SECONDS);
        sale.assertComplete().assertNoErrors();
        sale.dispose(); // a screen disposing a finished sale
        assertEquals("finishing is not a cancel", 0, terminal.searchCancels.get());
        terminal.acceptCard(TransactionType.SALE, 2000).test().assertNoErrors();
        assertEquals(2000L, terminal.nextSearch().config.getAmountMinor());
    }

    @Test
    public void aSearchThatStopsWithNoOutcomeStillEndsItsTransaction() throws Exception {
        TestObserver<Void> sale = terminal.acceptCard(TransactionType.SALE, 1000).test();
        Search first = terminal.nextSearch();

        first.release.countDown(); // returns without any listener callback
        sale.await(5, TimeUnit.SECONDS);

        sale.assertComplete();
        assertFalse("the engine stayed claimed", terminal.engine.isRunning());
        assertTrue(hasStep(TransactionStep.ERROR));
        assertEquals("the next sale runs", 2000L, startWhenFree(2000).config.getAmountMinor());
    }

    @Test
    public void aSecondDisposeCancelsNothingMore() throws Exception {
        TestObserver<Void> sale = terminal.acceptCard(TransactionType.SALE, 1000).test();
        Search first = terminal.nextSearch();

        sale.dispose();
        sale.dispose();
        terminal.dispose(); // the terminal going away cancels its transaction, once

        assertEquals(1, terminal.searchCancels.get());
        first.release.countDown();
    }

    @Test
    public void engineRefusesASecondBeginWithoutEndingTheFirst() {
        assertTrue(terminal.engine.begin());
        events.clear();

        assertFalse(terminal.engine.begin());

        assertTrue(terminal.engine.isRunning());
        assertTrue("got " + events, events.isEmpty());
    }

    /**
     * A transaction for 1000, cancelled, whose listener is still held (like a vendor reporting
     * late, from its own thread), then the next transaction for 2000, searching.
     */
    private Search cancelledSearchThenNextTransaction() throws InterruptedException {
        TestObserver<Void> sale = terminal.acceptCard(TransactionType.SALE, 1000).test();
        Search first = terminal.nextSearch();
        sale.dispose();
        first.release.countDown();
        Search second = startWhenFree(2000);
        terminal.searches.add(second); // hand it back for tests that need it
        events.clear();
        return first;
    }

    /** Starts a sale as soon as the terminal stops being busy, and returns its search. */
    private Search startWhenFree(long amount) throws InterruptedException {
        assertTrue("terminal stayed busy", awaitTrue(() -> started(amount)));
        return terminal.nextSearch();
    }

    /** Subscribes a sale; {@code false} if it was refused as busy. */
    private boolean started(long amount) {
        try {
            terminal.acceptCard(TransactionType.SALE, amount).test().assertNoErrors();
            return true;
        } catch (AssertionError busy) {
            return false;
        }
    }

    /** The terminal's one transaction thread, to hold it busy from a test. */
    private Scheduler transactionScheduler() throws ReflectiveOperationException {
        Field field = PosTerminal.class.getDeclaredField("transactionScheduler");
        field.setAccessible(true);
        return (Scheduler) field.get(terminal);
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
        final AtomicInteger searchCancels = new AtomicInteger();
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
            searchCancels.incrementAndGet();
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
