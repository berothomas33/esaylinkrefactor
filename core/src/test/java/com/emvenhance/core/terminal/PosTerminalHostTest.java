package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * The terminal owns the host and the printer: a behavior's online request reaches the host
 * through {@code engine.authorize → PosTerminal.authorize}, and receipts go to the terminal's
 * own printer.
 */
public class PosTerminalHostTest {

    private static final TransactionConfig CONFIG =
            new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);

    private final List<TransactionConfig> sentToHost = new ArrayList<>();
    private final List<List<String>> printed = new CopyOnWriteArrayList<>();
    private final CountDownLatch printDone = new CountDownLatch(1);

    @Test
    public void behaviorsOnlineRequestGoesThroughTheTerminalsHost() {
        TestTerminal terminal = new TestTerminal(config -> {
            sentToHost.add(config);
            return Single.just(AuthResult.approved("123456", "00", null, null));
        });
        List<TransactionStep> steps = new ArrayList<>();
        terminal.transactionSteps().subscribe(event -> steps.add(event.getStep()));

        AuthResult result = terminal.engine.authorize(CONFIG);

        assertTrue(result.isApproved());
        assertEquals(1, sentToHost.size());
        assertSame(CONFIG, sentToHost.get(0));
        assertEquals(Arrays.asList(TransactionStep.IDLE, TransactionStep.ONLINE_REQUIRED,
                TransactionStep.ONLINE_PROCESSING, TransactionStep.ONLINE_COMPLETED), steps);
    }

    @Test
    public void noHostAnswerIsOnlineFailedNotADecline() {
        TestTerminal terminal = new TestTerminal(
                config -> Single.error(new RuntimeException("timeout")));

        AuthResult result = terminal.engine.authorize(CONFIG);

        assertTrue(result.isFailed());
        assertEquals("timeout", result.getMessage());
    }

    @Test
    public void receiptsGoToTheTerminalsPrinter() throws InterruptedException {
        TestTerminal terminal = new TestTerminal(config -> Single.never());

        terminal.printReceipt(Collections.singletonList("APPROVED"));

        assertTrue("never printed", printDone.await(5, TimeUnit.SECONDS));
        assertEquals(Collections.singletonList(Collections.singletonList("APPROVED")), printed);
    }

    private final class TestTerminal extends PosTerminal {

        TestTerminal(CommunicationBehavior host) {
            super(stub(EmvBehavior.class), host, lines -> Completable.fromAction(() -> {
                printed.add(lines);
                printDone.countDown();
            }));
        }

        @Override
        public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
            return null;
        }

        @Override
        protected void cancelCardSearch() {
        }
    }

    /** Interface stub whose methods do nothing and return a default value. */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
    }
}
