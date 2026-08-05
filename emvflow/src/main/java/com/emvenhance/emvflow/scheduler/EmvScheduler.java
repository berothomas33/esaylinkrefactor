package com.emvenhance.emvflow.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-threaded EMV worker. All {@code execute} / {@code onSignal} / transitions
 * should run here when driven from multiple threads.
 */
public final class EmvScheduler {

    private final ExecutorService executor;

    public EmvScheduler() {
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "emv-scheduler-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
