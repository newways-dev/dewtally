package com.kate.dewtally.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Outbox messages not yet sent. Normally close to 0; a growing number means RabbitMQ is unreachable.
 */
@Component
class OutboxMetrics implements MeterBinder {

    private final OutboxRepository repository;

    OutboxMetrics(OutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("dewtally.outbox.pending", repository::countBySentAtIsNull)
                .description("Outbox messages not yet sent to RabbitMQ")
                .register(registry);
    }
}