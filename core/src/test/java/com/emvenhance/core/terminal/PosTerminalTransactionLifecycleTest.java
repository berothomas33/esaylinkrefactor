package com.emvenhance.core.terminal;

import io.reactivex.rxjava3.subjects.PublishSubject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * One transaction at a time: a start while the previous one hasn't finished — even cancelled, with
 * its search still stopping — is refused as "terminal busy", never queued. And a search that
 * outlives its transaction can't fail, or start EMV for, the next one.
 *
 * <p>The terminal owns the whole life of a transaction: {@code startTransaction} returns it (or
 * throws busy), {@code cancel()} cancels that one only, and its end — COMPLETED / ERROR, exactly
 * once — is published after the terminal is free.
 */
public class PosTerminalTransactionLifecycleTest {

    private TestTerminal terminal;
    private final List<TransactionStepEvent> events = new CopyOnWriteArrayList<>();
    private final List<Long> emvStartedWithAmount = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() {
        terminal = new TestTerminal();
        terminal.allSteps.subscribe(events::add);
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
        assertTrue(terminal.last.isRunning());
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
        PosTerminal.Transaction sale = terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        sale.cancel();
        events.clear();

        assertThrows(TerminalBusyException.class,
                () -> terminal.acceptCard(TransactionType.SALE, 2000));
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

        assertThrows(TerminalBusyException.class,
                () -> terminal.acceptCard(TransactionType.SALE, 2000));

        assertNull(terminal.searches.poll(300, TimeUnit.MILLISECONDS));
        assertTrue("first transaction got " + events, events.isEmpty());
        assertTrue(terminal.last.isRunning());
        first.listener.onChipDetected(CardPresence.chip());
        assertEquals(Collections.singletonList(1000L), emvStartedWithAmount);
    }

    @Test
    public void aCancelSaysCancellingAtOnceAndItsErrorComesOnceTheTerminalIsFree() throws Exception {
        PosTerminal.Transaction sale = terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        events.clear();

        sale.cancel();

        assertTrue(terminal.isSearchCancelled());
        assertFalse("the EMV winds down", sale.isRunning());
        assertTrue(sale.isCancelled());
        assertEquals(1, terminal.searchCancels.get());
        assertEquals("at once, not the end yet", java.util.Collections.singletonList(
                TransactionStep.CANCELLING), steps());

        first.listener.onSearchCancelled(); // what the vendor reports as its poll stops
        first.release.countDown();
        TransactionStepEvent end = terminal.awaitEnd();
        assertEquals(TransactionStep.CANCELLED, end.getStep());
        assertEquals(java.util.Arrays.asList(TransactionStep.CANCELLING, TransactionStep.CANCELLED),
                steps());
    }

    @Test
    public void aCancelRacingTheEndNeverSaysCancellingAfterIt() throws Exception {
        for (int i = 0; i < 200; i++) {
            terminal.ends.clear();
            events.clear();
            PosTerminal.Transaction sale = terminal.acceptCard(TransactionType.SALE, 1000 + i);
            Search search = terminal.nextSearch();
            Thread canceller = new Thread(sale::cancel);
            search.release.countDown(); // the transaction ends on its own...
            canceller.start();          // ...while the screen cancels it
            canceller.join(5_000);
            assertTrue("no end", awaitTrue(() -> count(TransactionStep.ERROR) + count(TransactionStep.CANCELLED) == 1));
            List<TransactionStep> steps = steps();
            assertTrue("ends last: " + steps, steps.get(steps.size() - 1).isEnd());
            assertTrue("at most one CANCELLING: " + steps, count(TransactionStep.CANCELLING) <= 1);
            assertTrue("terminal stayed busy", awaitTrue(() -> terminal.engineFree()));
        }
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
        PosTerminal.Transaction sale = terminal.acceptCard(TransactionType.SALE, 1000);
        sale.cancel(); // its work is still queued behind the held thread

        hold.countDown();

        Search next = startWhenFree(2000);
        assertEquals("the cancelled one never searched", 2000L, next.config.getAmountMinor());
        assertEquals("nothing searching to stop", 1, terminal.searchCancels.get());
    }

    @Test
    public void theEndIsPublishedOnceTheTerminalIsFree() throws Exception {
        List<Throwable> startOnEnd = new CopyOnWriteArrayList<>();
        terminal.allSteps.filter(e -> e.getStep().isEnd()).take(1).subscribe(end -> {
            try {
                terminal.acceptCard(TransactionType.SALE, 2000); // a screen's "Retry" right away
            } catch (Throwable t) {
                startOnEnd.add(t);
            }
        });
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();

        first.listener.onSearchTimeout(); // like a vendor: one callback, then the search returns
        first.release.countDown();

        assertEquals(TransactionStep.ERROR, terminal.awaitEnd().getStep());
        assertTrue("busy on the end: " + startOnEnd, startOnEnd.isEmpty());
        assertEquals(2000L, terminal.nextSearch().config.getAmountMinor());
        assertEquals("finishing is not a cancel", 0, terminal.searchCancels.get());
    }

    @Test
    public void aTransactionEndsOnceWhateverItsCodeReportsAfter() throws Exception {
        PosTerminal.Transaction sale = terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        events.clear();

        first.listener.onSearchTimeout();                 // the end
        sale.publish(TransactionStepEvent.of(TransactionStep.APPROVED)); // code still unwinding
        sale.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        sale.publish(PosTerminal.errorEvent("Transaction cancelled"));
        first.release.countDown();

        TransactionStepEvent end = terminal.awaitEnd();
        assertEquals("No card presented — card search timed out", end.getMessage());
        assertEquals(java.util.Collections.singletonList(TransactionStep.ERROR), steps());
    }

    @Test
    public void aSearchThatStopsWithNoOutcomeStillEndsItsTransaction() throws Exception {
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();

        first.release.countDown(); // returns without any listener callback

        TransactionStepEvent end = terminal.awaitEnd();
        assertEquals("Transaction ended without an outcome", end.getMessage());
        assertFalse(terminal.last.isRunning());
        assertEquals("the next sale runs", 2000L, startWhenFree(2000).config.getAmountMinor());
    }

    @Test
    public void aCancelAfterTheEndAndASecondCancelDoNothing() throws Exception {
        PosTerminal.Transaction sale = terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();

        sale.cancel();
        sale.cancel();
        terminal.dispose(); // the terminal going away cancels its transaction, once
        assertEquals(1, terminal.searchCancels.get());

        first.release.countDown();
        terminal.awaitEnd();
        sale.cancel(); // a screen cancelling a finished sale
        assertEquals(1, terminal.searchCancels.get());
    }

    @Test
    public void nothingIsPublishedOutsideATransaction() {
        terminal.authorize(new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.ANY));

        assertTrue("ONLINE_* with no transaction: " + events, events.isEmpty());
        assertFalse(terminal.isBusy());
    }

    /**
     * A transaction for 1000, cancelled, whose listener is still held (like a vendor reporting
     * late, from its own thread), then the next transaction for 2000, searching.
     */
    private Search cancelledSearchThenNextTransaction() throws InterruptedException {
        PosTerminal.Transaction sale = terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        sale.cancel();
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

    /** Starts a sale; {@code false} if it was refused as busy. */
    private boolean started(long amount) {
        try {
            terminal.acceptCard(TransactionType.SALE, amount);
            return true;
        } catch (TerminalBusyException busy) {
            return false;
        }
    }

    /** The terminal's one transaction thread, to hold it busy from a test. */
    private Executor transactionExecutor() throws ReflectiveOperationException {
        Field field = PosTerminal.class.getDeclaredField("transactionExecutor");
        field.setAccessible(true);
        return (Executor) field.get(terminal);
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

    private List<TransactionStep> steps() {
        List<TransactionStep> steps = new java.util.ArrayList<>();
        for (TransactionStepEvent e : events) {
            steps.add(e.getStep());
        }
        return steps;
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

        /** Every transaction's steps, for the test: each transaction has its own stream. */
        final PublishSubject<TransactionStepEvent> allSteps = PublishSubject.create();

        /** The last transaction started. */
        volatile Transaction last;

        @Override
        public Transaction startTransaction(TransactionConfig config) {
            Transaction transaction = super.startTransaction(config);
            last = transaction;
            transaction.steps().subscribe(allSteps::onNext);
            return transaction;
        }

        final LinkedBlockingQueue<Search> searches = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<TransactionStepEvent> ends = new LinkedBlockingQueue<>();
        final AtomicInteger searchCancels = new AtomicInteger();
        private final List<Search> all = new CopyOnWriteArrayList<>();

        TestTerminal() {
            super(behavior(), stub(CommunicationBehavior.class), stub(PrinterBehavior.class));
            allSteps.filter(e -> e.getStep().isEnd()).subscribe(ends::add);
        }

        /** The terminal is free (its current transaction is gone). */
        boolean engineFree() {
            return !isBusy();
        }

        /** The next transaction's end, as published once the terminal is free. */
        TransactionStepEvent awaitEnd() throws InterruptedException {
            TransactionStepEvent end = ends.poll(5, TimeUnit.SECONDS);
            assertNotNull("no end published", end);
            return end;
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
                        emvStartedWithAmount.add(((TransactionConfig) args[0]).getAmountMinor());
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
