package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.transaction.TransactionType;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.transaction.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.Test;

public class PosTerminalCloseAtHostTest {

    private final BlockingQueue<String> closings = new LinkedBlockingQueue<>();
    private final List<TransactionConfig> sent = new CopyOnWriteArrayList<>();
    private final List<Boolean> busyAtClosing = new CopyOnWriteArrayList<>();
    private final List<Thread> closingThreads = new CopyOnWriteArrayList<>();
    private final BlockingQueue<AuthResult> answers = new LinkedBlockingQueue<>();
    private volatile RuntimeException hostError = new RuntimeException("down");

    private final CommunicationBehavior host = new CommunicationBehavior() {
        @Override
        public Single<AuthResult> authorize(TransactionConfig config) {
            sent.add(config);
            AuthResult answer = answers.poll();
            return answer != null ? Single.just(answer) : Single.error(hostError);
        }

        @Override
        public Completable acknowledge(TransactionConfig config) {
            return closing("ACK " + config.getAsyncRequestId());
        }

        @Override
        public Completable reverse(TransactionConfig config) {
            return closing("REVERSE " + config.getAsyncRequestId());
        }
    };

    private TestTerminal terminal;

    @Test(timeout = 10_000)
    public void hostApprovedAndCardCompletedIsAcknowledged() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.APPROVED));
        });

        terminal.runOne();

        assertEquals("ACK " + idSent(0), closings.take());
        assertNoMoreClosings();
    }

    @Test(timeout = 10_000)
    public void hostApprovedButCardDeclinedIsReversed() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.DECLINED));
        });

        terminal.runOne();

        assertEquals("REVERSE " + idSent(0), closings.take());
        assertNoMoreClosings();
    }

    @Test(timeout = 10_000)
    public void hostApprovedThenCancelledIsReversed() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.cancel();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.APPROVED));
        });

        terminal.runOne();

        assertEquals(TransactionStep.CANCELLED, terminal.lastEnd);
        assertEquals("REVERSE " + idSent(0), closings.take());
        assertNoMoreClosings();
    }

    @Test(timeout = 10_000)
    public void hostApprovedThenAnErrorIsReversed() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.emvFinished(PosTerminal.errorEvent("card removed"));
        });

        terminal.runOne();

        assertEquals("REVERSE " + idSent(0), closings.take());
        assertNoMoreClosings();
    }

    @Test(timeout = 10_000)
    public void noHostAnswerIsReversedByTheIdItWasSentWith() throws InterruptedException {
        terminal = new TestTerminal(t -> {
            assertTrue(goOnline().isFailed());
            t.emvFinished(TransactionStepEvent.of(TransactionStep.DECLINED));
        });

        terminal.runOne();

        assertEquals("REVERSE " + idSent(0), closings.take());
        assertNoMoreClosings();
    }

    @Test(timeout = 10_000)
    public void aNotSentAnswerIsNotReversed() throws InterruptedException {
        answers.add(AuthResult.notSent("Cash In isn't supported online yet"));
        terminal = new TestTerminal(t -> {
            goOnline();
            t.emvFinished(PosTerminal.errorEvent("Online Failed"));
        });

        terminal.runOne();

        assertNoMoreClosings();
    }

    @Test(timeout = 10_000)
    public void hostDeclinedHasNothingToClose() throws InterruptedException {
        answers.add(AuthResult.declined("05", "Do not honour"));
        terminal = new TestTerminal(t -> {
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.DECLINED));
        });

        terminal.runOne();

        assertNoMoreClosings();
    }

    @Test(timeout = 10_000)
    public void aTransactionThatNeverWentOnlineHasNothingToClose() throws InterruptedException {
        terminal = new TestTerminal(t -> {
            t.emvFinished(TransactionStepEvent.of(TransactionStep.DECLINED));
        });

        terminal.runOne();

        assertTrue(sent.isEmpty());
        assertNoMoreClosings();
    }

    @Test(timeout = 10_000)
    public void aRequestSupersededByALaterOneIsReversedAndTheLastAcknowledged()
            throws InterruptedException {
        answers.add(approved());
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.APPROVED));
        });

        terminal.runOne();

        assertNotEquals(idSent(0), idSent(1));
        assertEquals(Arrays.asList("REVERSE " + idSent(0), "ACK " + idSent(1)),
                Arrays.asList(closings.take(), closings.take()));
        assertNoMoreClosings();
    }

    @Test(timeout = 10_000)
    public void eachRequestIsNamedBeforeItIsSentAndKeepsTheRest() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.APPROVED));
        });

        terminal.runOne();

        assertNotNull(idSent(0));
        assertEquals(TransactionType.SALE, sent.get(0).getType());
        assertEquals(1000, sent.get(0).getAmountMinor());
        closings.take();
    }

    @Test(timeout = 10_000)
    public void sentBeforeTheEndOnTheTransactionThreadWithTheTerminalBusy()
            throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.APPROVED));
        });

        terminal.runOne();

        assertEquals("ACK " + idSent(0), closings.poll());
        assertEquals(Collections.singletonList(true), busyAtClosing);
        assertEquals("the transaction thread", terminal.transactionThread, closingThreads.get(0));
        List<TransactionStep> steps = terminal.lastSteps;
        assertEquals(Arrays.asList(TransactionStep.APPROVED, TransactionStep.ACKNOWLEDGING,
                TransactionStep.CLOSED, TransactionStep.COMPLETED),
                steps.subList(steps.size() - 4, steps.size()));
    }

    @Test(timeout = 10_000)
    public void eachClosingReportsItsResultBeforeTheEnd() throws InterruptedException {
        answers.add(approved());
        List<TransactionStepEvent> events = new CopyOnWriteArrayList<>();
        CommunicationBehavior refusing = new CommunicationBehavior() {
            @Override
            public Single<AuthResult> authorize(TransactionConfig config) {
                return host.authorize(config);
            }

            @Override
            public Completable acknowledge(TransactionConfig config) {
                return Completable.error(new IllegalStateException("HTTP 500"));
            }
        };
        terminal = new TestTerminal(refusing, t -> {
            t.steps().subscribe(events::add);
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.APPROVED));
        });

        terminal.runOne();

        TransactionStepEvent closed = null;
        for (TransactionStepEvent e : events) {
            if (e.getStep() == TransactionStep.CLOSED) {
                closed = e;
            }
        }
        assertTrue("no CLOSED: " + events, closed != null);
        assertEquals(Boolean.FALSE, closed.get(TransactionStepEvent.KEY_CLOSED_OK));
        assertEquals(idSent(0), closed.get(TransactionStepEvent.KEY_REQUEST_ID));
        assertTrue(closed.getMessage(),
                closed.getMessage().contains("failed — not retried: HTTP 500"));
    }

    @Test(timeout = 10_000)
    public void aClosingTheHostTookReportsItClosed() throws InterruptedException {
        answers.add(approved());
        List<TransactionStepEvent> events = new CopyOnWriteArrayList<>();
        terminal = new TestTerminal(t -> {
            t.steps().subscribe(events::add);
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.APPROVED));
        });

        terminal.runOne();

        TransactionStepEvent closed = null;
        for (TransactionStepEvent e : events) {
            if (e.getStep() == TransactionStep.CLOSED) {
                closed = e;
            }
        }
        assertTrue("no CLOSED: " + events, closed != null);
        assertEquals(Boolean.TRUE, closed.get(TransactionStepEvent.KEY_CLOSED_OK));
        assertEquals("Acknowledgement " + idSent(0) + " sent", closed.getMessage());
    }

    @Test(timeout = 10_000)
    public void aClosingTheHostNeverAnswersEndsAfterItsTimeout() throws InterruptedException {
        CommunicationBehavior silent = new CommunicationBehavior() {
            @Override
            public Single<AuthResult> authorize(TransactionConfig config) {
                return host.authorize(config);
            }

            @Override
            public Completable reverse(TransactionConfig config) {
                return Completable.never();
            }
        };
        answers.add(approved());
        terminal = new TestTerminal(silent, t -> {
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.DECLINED));
        });
        terminal.closeAtHostTimeoutMs = 200;

        long started = System.currentTimeMillis();
        terminal.runOne();

        assertTrue("waited " + (System.currentTimeMillis() - started) + " ms",
                System.currentTimeMillis() - started < 3_000);
        assertEquals(TransactionStep.COMPLETED, terminal.lastEnd);
        assertTrue(terminal.lastSteps.contains(TransactionStep.REVERSING));
        assertTrue("the terminal is free", !terminal.isBusy());
    }

    @Test(timeout = 10_000)
    public void aFailedClosingIsLoggedAndTheTerminalGoesOn() throws InterruptedException {
        List<String> calls = new CopyOnWriteArrayList<>();
        CountDownLatch called = new CountDownLatch(2);
        answers.add(approved());
        answers.add(approved());
        CommunicationBehavior failing = new CommunicationBehavior() {
            @Override
            public Single<AuthResult> authorize(TransactionConfig config) {
                return host.authorize(config);
            }

            @Override
            public Completable acknowledge(TransactionConfig config) {
                calls.add("ACK");
                called.countDown();
                throw new IllegalStateException("built badly");
            }
        };
        terminal = new TestTerminal(failing, t -> {
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.APPROVED));
        });

        terminal.runOne();
        terminal.runOne();

        assertTrue(called.await(5, TimeUnit.SECONDS));
        assertEquals(Arrays.asList("ACK", "ACK"), calls);
    }

    @Test(timeout = 10_000)
    public void aCancelDuringASlowAcknowledgementDoesNotWaitForIt() throws Exception {
        CountDownLatch ackStarted = new CountDownLatch(1);
        CountDownLatch releaseAck = new CountDownLatch(1);
        AtomicReference<PosTerminal.Transaction> sale = new AtomicReference<>();
        answers.add(approved());
        CommunicationBehavior slow = new CommunicationBehavior() {
            @Override
            public Single<AuthResult> authorize(TransactionConfig config) {
                return host.authorize(config);
            }

            @Override
            public Completable acknowledge(TransactionConfig config) {
                return Completable.fromAction(() -> {
                    ackStarted.countDown();
                    releaseAck.await(5, TimeUnit.SECONDS);
                });
            }
        };
        terminal = new TestTerminal(slow, t -> {
            sale.set(t);
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.APPROVED));
        });
        Thread run = new Thread(() -> {
            try {
                terminal.runOne();
            } catch (InterruptedException ignored) {
                // the test's own thread
            }
        });
        run.start();
        assertTrue(ackStarted.await(5, TimeUnit.SECONDS));

        long started = System.nanoTime();
        sale.get().cancel(); // the screen's Cancel while the acknowledgement waits on the host
        long tookMs = (System.nanoTime() - started) / 1_000_000;
        releaseAck.countDown();
        run.join(5_000);

        assertTrue("Cancel waited " + tookMs + " ms for the acknowledgement", tookMs < 500);
        assertEquals(TransactionStep.COMPLETED, terminal.lastEnd);
    }

    private Completable closing(String what) {
        return Completable.fromAction(() -> {
            busyAtClosing.add(terminal.isBusy());
            closingThreads.add(Thread.currentThread());
            closings.add(what);
        });
    }

    private AuthResult goOnline() {
        return terminal.authorize(
                new TransactionConfig(TransactionType.SALE, 1000,
                        com.emvenhance.core.card.EntryMethod.CHIP));
    }

    private String idSent(int i) {
        return sent.get(i).getAsyncRequestId();
    }

    private static AuthResult approved() {
        return AuthResult.approved("123456", "00", null, null);
    }

    private void assertNoMoreClosings() throws InterruptedException {
        answers.clear();
        answers.add(approved());
        terminal.script = t -> {
            goOnline();
            t.emvFinished(TransactionStepEvent.of(TransactionStep.APPROVED));
        };
        terminal.runOne();
        assertEquals("ACK " + idSent(sent.size() - 1), closings.take());
        assertTrue("left over: " + closings, closings.isEmpty());
    }

    private final class TestTerminal extends PosTerminal {

        volatile Consumer<Transaction> script;
        volatile TransactionStep lastEnd;
        volatile List<TransactionStep> lastSteps;
        volatile Thread transactionThread;
        volatile long closeAtHostTimeoutMs = 15_000;

        @Override
        protected long closeAtHostTimeoutMs() {
            return closeAtHostTimeoutMs;
        }

        TestTerminal(Consumer<Transaction> script) {
            this(host, script);
        }

        TestTerminal(CommunicationBehavior host, Consumer<Transaction> script) {
            super(scripted(), host, lines -> Completable.complete());
            this.script = script;
        }

        void runOne() throws InterruptedException {
            CountDownLatch ended = new CountDownLatch(1);
            List<TransactionStep> steps = new CopyOnWriteArrayList<>();
            lastSteps = steps;
            startTransaction(new TransactionConfig(TransactionType.SALE, 1000,
                    com.emvenhance.core.card.EntryMethod.CHIP))
                    .steps().subscribe(e -> {
                        steps.add(e.getStep());
                        if (e.getStep().isEnd()) {
                            lastEnd = e.getStep();
                            ended.countDown();
                        }
                    });
            assertTrue("never ended", ended.await(5, TimeUnit.SECONDS));
        }

    }

    private AbstractEmvBehavior scripted() {
        return StubBehavior.of((name, args) -> {
            switch (name) {
                case "search":
                    terminal.transactionThread = Thread.currentThread();
                    ((CardSearchListener) args[1]).onChipDetected(CardPresence.chip());
                    return null;
                case "start":
                    terminal.script.accept((PosTerminal.Transaction) args[2]);
                    return null;
                default:
                    return null;
            }
        });
    }
}
