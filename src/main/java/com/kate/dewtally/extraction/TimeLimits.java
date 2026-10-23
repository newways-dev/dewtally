package com.kate.dewtally.extraction;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * Runs a task with a time limit, so a hanging call (slow model, pathological PDF)
 * cannot block an upload forever.
 * <p>
 * Limitation: Java cannot force-stop a thread. On timeout the task is interrupted and the caller
 * continues, but code that ignores interruption keeps running in the background until it ends.
 * For CPU-heavy work on untrusted input use a {@link Bulkhead}, which also limits how many such
 * tasks can run at once. Real isolation comes with the separate worker in layer 4.
 */
public final class TimeLimits {

    private TimeLimits() {
    }

    /**
     * Runs the task on its own virtual thread. Suited for waiting-heavy work like network calls.
     */
    public static <T> T call(Callable<T> task, Duration limit, String description) {
        FutureTask<T> future = new FutureTask<>(task);
        Thread.ofVirtual().name("time-limited-" + description).start(future);
        return await(future, limit, description);
    }

    /**
     * Waits for an already started task, giving up after the limit.
     */
    static <T> T await(Future<T> future, Duration limit, String description) {
        try {
            return future.get(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeLimitExceededException(description + " took longer than " + limit);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + description, e);
        } catch (ExecutionException e) {
            // Rethrow the task's own exception unchanged, so callers can catch it as usual.
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(description + " failed", cause);
        }
    }
}