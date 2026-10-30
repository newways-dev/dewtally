package com.kate.dewtally.extraction;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BulkheadTest {

    // The worker becomes free a moment after the stuck task ends; retry briefly.
    private static String callWhenFree(Bulkhead bulkhead) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                return bulkhead.call(() -> "next", Duration.ofSeconds(1));
            } catch (BulkheadFullException e) {
                Thread.sleep(20);
            }
        }
        throw new AssertionError("Bulkhead did not recover");
    }

    @Test
    void runsTasksAndReturnsTheirResult() {
        try (Bulkhead bulkhead = new Bulkhead("test", 2, 2)) {
            assertThat(bulkhead.call(() -> 42, Duration.ofSeconds(1))).isEqualTo(42);
        }
    }

    @Test
    void stuckTaskBlocksOnlyItsOwnWorkerAndNewTasksAreRejected() throws InterruptedException {
        AtomicBoolean released = new AtomicBoolean(false);

        try (Bulkhead bulkhead = new Bulkhead("test", 1, 0)) {
            // A task that ignores interruption, like a CPU-bound parser on a pathological file.
            assertThatThrownBy(() -> bulkhead.call(() -> {
                while (!released.get()) {
                    Thread.onSpinWait();
                }
                return "finally done";
            }, Duration.ofMillis(100)))
                    .isInstanceOf(TimeLimitExceededException.class);

            // The only worker is still stuck: new work is rejected at once instead of piling up.
            assertThatThrownBy(() -> bulkhead.call(() -> "next", Duration.ofSeconds(1)))
                    .isInstanceOf(BulkheadFullException.class);

            // Once the stuck task ends, the bulkhead works again.
            released.set(true);
            assertThat(callWhenFree(bulkhead)).isEqualTo("next");
        }
    }
}