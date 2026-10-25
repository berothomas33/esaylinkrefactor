package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.HostNotReachedException;
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

/**
 * Once a transaction that went online has ended, the terminal closes the host's side of each
 * request it sent: acknowledge the sale the host approved and the card completed, reverse any
 * other the host approved or never answered, nothing for a decline. Sent after the end, on its
 * own thread, named by the asyncRequestId the request went with.
 */
public class PosTerminalHostFollowUpTest {

    private final BlockingQueue<String> followUps = new LinkedBlockingQueue<>();
    private final List<TransactionConfig> sent = new CopyOnWriteArrayList<>();
    private final List<Boolean> busyAtFollowUp = new CopyOnWriteArrayList<>();
    private final List<Thread> followUpThreads = new CopyOnWriteArrayList<>();
    private final BlockingQueue<AuthResult> answers = new LinkedBlockingQueue<>();
    /** What the host fails with when no answer is queued. */
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
            return followUp("ACK " + config.getAsyncRequestId());
        }

        @Override
        public Completable reverse(TransactionConfig config) {
            return followUp("REVERSE " + config.getAsyncRequestId());
        }
    };

    private TestTerminal terminal;

    @Test(timeout = 10_000)
    public void hostApprovedAndCardCompletedIsAcknowledged() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.publish(TransactionStepEvent.of(TransactionStep.APPROVED));
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        });

        terminal.runOne();

        assertEquals("ACK " + idSent(0), followUps.take());
        assertNoMoreFollowUps();
    }

    @Test(timeout = 10_000)
    public void hostApprovedButCardDeclinedIsReversed() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            // AAC at the 2nd GENERATE AC
            t.publish(TransactionStepEvent.of(TransactionStep.DECLINED));
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        });

        terminal.runOne();

        assertEquals("REVERSE " + idSent(0), followUps.take());
        assertNoMoreFollowUps();
    }

    @Test(timeout = 10_000)
    public void hostApprovedThenCancelledIsReversed() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.cancel();
            t.publish(TransactionStepEvent.of(TransactionStep.APPROVED)); // dropped: ended
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        });

        terminal.runOne();

        assertEquals(TransactionStep.CANCELLED, terminal.lastEnd);
        assertEquals("REVERSE " + idSent(0), followUps.take());
        assertNoMoreFollowUps();
    }

    @Test(timeout = 10_000)
    public void hostApprovedThenAnErrorIsReversed() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.publish(PosTerminal.errorEvent("card removed"));
        });

        terminal.runOne();

        assertEquals("REVERSE " + idSent(0), followUps.take());
        assertNoMoreFollowUps();
    }

    @Test(timeout = 10_000)
    public void noHostAnswerIsReversedByTheIdItWasSentWith() throws InterruptedException {
        // no answer queued: the host errors
        terminal = new TestTerminal(t -> {
            assertTrue(goOnline().isFailed());
            t.publish(TransactionStepEvent.of(TransactionStep.DECLINED));
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        });

        terminal.runOne();

        assertEquals("REVERSE " + idSent(0), followUps.take());
        assertNoMoreFollowUps();
    }

    @Test(timeout = 10_000)
    public void hostNotReachedIsNotReversed() throws InterruptedException {
        hostError = new HostNotReachedException("Exchange: HTTP 403 Forbidden");
        AtomicReference<AuthResult> answer = new AtomicReference<>();
        terminal = new TestTerminal(t -> {
            answer.set(goOnline());
            t.publish(PosTerminal.errorEvent("Online Failed"));
        });

        terminal.runOne();

        assertTrue(answer.get().isFailed());
        assertTrue(answer.get().isNotSent());
        assertEquals("Exchange: HTTP 403 Forbidden", answer.get().getMessage());
        assertNoMoreFollowUps();
    }

    @Test(timeout = 10_000)
    public void aNotSentAnswerIsNotReversed() throws InterruptedException {
        answers.add(AuthResult.notSent("Cash In isn't supported online yet"));
        terminal = new TestTerminal(t -> {
            goOnline();
            t.publish(PosTerminal.errorEvent("Online Failed"));
        });

        terminal.runOne();

        assertNoMoreFollowUps();
    }

    @Test(timeout = 10_000)
    public void hostDeclinedHasNothingToClose() throws InterruptedException {
        answers.add(AuthResult.declined("05", "Do not honour"));
        terminal = new TestTerminal(t -> {
            goOnline();
            t.publish(TransactionStepEvent.of(TransactionStep.DECLINED));
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        });

        terminal.runOne();

        assertNoMoreFollowUps();
    }

    @Test(timeout = 10_000)
    public void aTransactionThatNeverWentOnlineHasNothingToClose() throws InterruptedException {
        terminal = new TestTerminal(t -> {
            t.publish(TransactionStepEvent.of(TransactionStep.DECLINED)); // offline decline
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        });

        terminal.runOne();

        assertTrue(sent.isEmpty());
        assertNoMoreFollowUps();
    }

    @Test(timeout = 10_000)
    public void aRequestSupersededByALaterOneIsReversedAndTheLastAcknowledged()
            throws InterruptedException {
        answers.add(approved());
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            goOnline();
            t.publish(TransactionStepEvent.of(TransactionStep.APPROVED));
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        });

        terminal.runOne();

        assertNotEquals(idSent(0), idSent(1));
        assertEquals(Arrays.asList("REVERSE " + idSent(0), "ACK " + idSent(1)),
                Arrays.asList(followUps.take(), followUps.take()));
        assertNoMoreFollowUps();
    }

    @Test(timeout = 10_000)
    public void eachRequestIsNamedBeforeItIsSentAndKeepsTheRest() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.publish(TransactionStepEvent.of(TransactionStep.APPROVED));
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        });

        terminal.runOne();

        assertNotNull(idSent(0));
        assertEquals(TransactionType.SALE, sent.get(0).getType());
        assertEquals(1000, sent.get(0).getAmountMinor());
        followUps.take();
    }

    @Test(timeout = 10_000)
    public void sentAfterTheEndOnItsOwnThreadWithTheTerminalFree() throws InterruptedException {
        answers.add(approved());
        terminal = new TestTerminal(t -> {
            goOnline();
            t.publish(TransactionStepEvent.of(TransactionStep.APPROVED));
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        });

        terminal.runOne();
        followUps.take();

        assertEquals(Collections.singletonList(false), busyAtFollowUp);
        assertNotEquals("not the transaction thread",
                terminal.transactionThread, followUpThreads.get(0));
    }

    @Test(timeout = 10_000)
    public void aFailedFollowUpIsLoggedAndTheTerminalGoesOn() throws InterruptedException {
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
                throw new IllegalStateException("built badly"); // even a throwing builder
            }
        };
        terminal = new TestTerminal(failing, t -> {
            goOnline();
            t.publish(TransactionStepEvent.of(TransactionStep.APPROVED));
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        });

        terminal.runOne();
        terminal.runOne();

        assertTrue(called.await(5, TimeUnit.SECONDS));
        assertEquals(Arrays.asList("ACK", "ACK"), calls);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private Completable followUp(String what) {
        return Completable.fromAction(() -> {
            busyAtFollowUp.add(terminal.isBusy());
            followUpThreads.add(Thread.currentThread());
            followUps.add(what);
        });
    }

    private AuthResult goOnline() {
        return terminal.behavior.engine().authorize(
                new TransactionConfig(TransactionType.SALE, 1000,
                        com.emvenhance.core.card.EntryMethod.CHIP));
    }

    private String idSent(int i) {
        return sent.get(i).getAsyncRequestId();
    }

    private static AuthResult approved() {
        return AuthResult.approved("123456", "00", null, null);
    }

    /**
     * Follow-ups go out one at a time, in order: once a later transaction's acknowledgement has
     * come, nothing more of the earlier one is coming.
     */
    private void assertNoMoreFollowUps() throws InterruptedException {
        answers.clear();
        answers.add(approved());
        terminal.script = t -> {
            goOnline();
            t.publish(TransactionStepEvent.of(TransactionStep.APPROVED));
            t.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        };
        terminal.runOne();
        assertEquals("ACK " + idSent(sent.size() - 1), followUps.take());
        assertTrue("left over: " + followUps, followUps.isEmpty());
    }

    /** A terminal whose card is found at once; the behavior's start runs the test's script. */
    private final class TestTerminal extends PosTerminal {

        volatile Consumer<Transaction> script;
        volatile TransactionStep lastEnd;
        volatile Thread transactionThread;

        TestTerminal(Consumer<Transaction> script) {
            this(host, script);
        }

        TestTerminal(CommunicationBehavior host, Consumer<Transaction> script) {
            super(scripted(), host, lines -> Completable.complete());
            this.script = script;
        }

        /** Runs one transaction to its end. */
        void runOne() throws InterruptedException {
            CountDownLatch ended = new CountDownLatch(1);
            startTransaction(new TransactionConfig(TransactionType.SALE, 1000,
                    com.emvenhance.core.card.EntryMethod.CHIP))
                    .steps().filter(e -> e.getStep().isEnd()).subscribe(e -> {
                        lastEnd = e.getStep();
                        ended.countDown();
                    });
            assertTrue("never ended", ended.await(5, TimeUnit.SECONDS));
        }

        @Override
        public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
            transactionThread = Thread.currentThread();
            listener.onChipDetected(CardPresence.chip());
            return null;
        }

        @Override
        protected void cancelCardSearch() {
        }
    }

    /** prepare() succeeds; start() runs the terminal's script with its transaction. */
    private AbstractEmvBehavior scripted() {
        return StubBehavior.of((proxy, method, args) -> {
            switch (method.getName()) {
                case "prepare":
                    return Boolean.TRUE;
                case "start":
                    terminal.script.accept((PosTerminal.Transaction) args[2]);
                    return null;
                default:
                    return null;
            }
        });
    }
}
