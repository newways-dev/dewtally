package com.kate.dewtally.extraction;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeLimitsTest {

    @Test
    void returnsTheResultWhenTheTaskIsFastEnough() {
        String result = TimeLimits.call(() -> "done", Duration.ofSeconds(1), "fast task");

        assertThat(result).isEqualTo("done");
    }

    @Test
    void stopsWaitingWhenTheTaskIsTooSlow() {
        long start = System.currentTimeMillis();

        assertThatThrownBy(() -> TimeLimits.call(() -> {
            Thread.sleep(5_000);
            return "too late";
        }, Duration.ofMillis(200), "slow task"))
                .isInstanceOf(TimeLimitExceededException.class)
                .hasMessageContaining("slow task");

        assertThat(System.currentTimeMillis() - start).isLessThan(2_000);
    }

    @Test
    void passesTheTasksOwnExceptionThrough() {
        assertThatThrownBy(() -> TimeLimits.call(() -> {
            throw new PdfReadException("broken file", null);
        }, Duration.ofSeconds(1), "failing task"))
                .isInstanceOf(PdfReadException.class)
                .hasMessage("broken file");
    }
}