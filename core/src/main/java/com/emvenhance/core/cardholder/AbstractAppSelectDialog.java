package com.emvenhance.core.cardholder;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public abstract class AbstractAppSelectDialog {

    public static final int NO_ANSWER = -1;

    private static final long TIMEOUT_MS = 30_000L;

    private static final long POLL_MS = 250L;

    private final CountDownLatch done = new CountDownLatch(1);
    private volatile int selectedIndex = NO_ANSWER;

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

    protected abstract void show(List<String> labels);

    protected abstract void dismiss();

    protected final void onSelected(int index) {
        selectedIndex = index;
        done.countDown();
    }

    protected final void onNoScreen() {
        selectedIndex = NO_ANSWER;
        done.countDown();
    }
}
