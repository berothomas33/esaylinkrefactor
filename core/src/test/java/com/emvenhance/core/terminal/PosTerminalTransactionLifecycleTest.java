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
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A search that outlives its transaction — cancelled, but the reader hasn't stopped yet — must not
 * fail, or start EMV for, the next transaction, and must not share the reader with it: every
 * transaction runs on the terminal's one transaction thread, in turn.
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
    public void theNextSearchWaitsForTheEarlierOneToStop() throws Exception {
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        terminal.cancelTransaction();

        terminal.acceptCard(TransactionType.SALE, 2000);
        assertNull("reader opened twice", terminal.searches.poll(300, TimeUnit.MILLISECONDS));

        first.release.countDown();
        Search second = terminal.nextSearch();
        assertEquals(2000L, second.config.getAmountMinor());
        assertTrue("old search kept searching", first.cancelledWhenReleased);
        assertSame("one transaction thread", first.thread, second.thread);
    }

    @Test
    public void aStartCancelledWhileWaitingItsTurnNeverOpensTheReader() throws Exception {
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        terminal.cancelTransaction();
        terminal.acceptCard(TransactionType.SALE, 2000); // waits for the first to stop
        terminal.cancelTransaction();                    // cancelled while still waiting
        terminal.acceptCard(TransactionType.SALE, 3000);

        first.release.countDown();

        assertEquals(3000L, terminal.nextSearch().config.getAmountMinor());
        assertNull(terminal.searches.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    public void aSecondStartWhileSearchingIsIgnoredAndTheFirstCarriesOn() throws Exception {
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        events.clear();

        terminal.acceptCard(TransactionType.SALE, 2000);

        assertNull(terminal.searches.poll(300, TimeUnit.MILLISECONDS));
        assertTrue("first transaction got " + events, events.isEmpty());
        assertTrue(terminal.engine.isRunning());
        first.listener.onChipDetected(CardPresence.chip());
        assertEquals(Collections.singletonList(1000L), emvStartedWithAmount);
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
     * A transaction for 1000 whose search is cancelled but only returns afterwards (its listener
     * still held, like a vendor reporting late), then the next transaction for 2000, searching.
     */
    private Search cancelledSearchThenNextTransaction() throws InterruptedException {
        terminal.acceptCard(TransactionType.SALE, 1000);
        Search first = terminal.nextSearch();
        terminal.cancelTransaction();
        terminal.acceptCard(TransactionType.SALE, 2000);
        first.release.countDown();
        Search second = terminal.nextSearch();
        terminal.searches.add(second); // hand it back for tests that need it
        events.clear();
        return first;
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
