package com.kate.dewtally.extraction;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * A small, separate pool for risky work (the "bulkhead" pattern from ship building:
 * a leak in one compartment does not sink the whole ship).

 * At most {@code maxConcurrent} tasks run at once, at most {@code maxQueued} wait.
 * A task that hangs keeps only its own worker busy; when all workers are stuck and the queue
 * is full, new tasks are rejected immediately instead of piling up and exhausting the server.

 * Uses platform threads on purpose: CPU-heavy work on virtual threads would block the small
 * shared carrier pool that all other virtual threads depend on.
 */
public final class Bulkhead implements AutoCloseable {

    private final String name;
    private final ThreadPoolExecutor executor;

    public Bulkhead(String name, int maxConcurrent, int maxQueued) {
        this.name = name;
        BlockingQueue<Runnable> queue = maxQueued > 0
                ? new ArrayBlockingQueue<>(maxQueued)
                : new SynchronousQueue<>(); // no waiting at all: run now or reject
        this.executor = new ThreadPoolExecutor(
                maxConcurrent, maxConcurrent,
                0L, TimeUnit.MILLISECONDS,
                queue,
                // Daemon threads: a stuck task never prevents the application from shutting down.
                Thread.ofPlatform().name(name + "-", 1).daemon(true).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Runs the task in this bulkhead and waits at most {@code limit} (queue time included).
     */
    public <T> T call(Callable<T> task, Duration limit) {
        Future<T> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException e) {
            throw new BulkheadFullException(name + " is busy, try again later");
        }
        return TimeLimits.await(future, limit, name);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}