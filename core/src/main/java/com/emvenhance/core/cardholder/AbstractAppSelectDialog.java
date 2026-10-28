package com.emvenhance.core.cardholder;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Multi-application selection: the cardholder picks which of the card's applications to use,
 * while the kernel waits on the transaction thread (its callback needs the index on the same
 * stack). The part every vendor shares lives here — the wait, the timeout, giving up when the
 * transaction ends, and "no answer" (never "pick the first"). The vendor's subclass turns its own
 * candidate data into labels and shows / closes the choice on screen.
 */
public abstract class AbstractAppSelectDialog {

    /** No answer: the cardholder didn't choose in time, the transaction ended, or no screen. */
    public static final int NO_ANSWER = -1;

    /** How long the cardholder has to choose; then {@link #NO_ANSWER}. */
    private static final long TIMEOUT_MS = 30_000L;

    /** How often the wait checks whether the transaction ended (a cancel closes the choice). */
    private static final long POLL_MS = 250L;

    private final CountDownLatch done = new CountDownLatch(1);
    private volatile int selectedIndex = NO_ANSWER;

    /**
     * Shows {@code labels} and blocks until the cardholder picks one: its index. {@link #NO_ANSWER}
     * (and the choice closed) once the timeout passes, or {@code ended} turns true (a cancel).
     */
    public final int selectBlocking(List<String> labels, BooleanSupplier ended) {
        show(labels);
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        try {
            while (!done.await(POLL_MS, TimeUnit.MILLISECONDS)) {
                if (ended.getAsBoolean() || System.currentTimeMillis() >= deadline) {
                    dismiss();
                    return NO_ANSWER;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dismiss();
            return NO_ANSWER;
        }
        return selectedIndex;
    }

    /** Puts the choice on screen (from the transaction thread: post it to the UI thread). */
    protected abstract void show(List<String> labels);

    /** Takes the choice off the screen; safe to call when it isn't there. */
    protected abstract void dismiss();

    /** The cardholder picked {@code index}. */
    protected final void onSelected(int index) {
        selectedIndex = index;
        done.countDown();
    }

    /** Nothing to show it on (no screen): no answer at once, never a wait with nothing shown. */
    protected final void onNoScreen() {
        selectedIndex = NO_ANSWER;
        done.countDown();
    }
}
