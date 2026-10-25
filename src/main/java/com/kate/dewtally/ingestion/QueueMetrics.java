package com.kate.dewtally.ingestion;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * How many messages wait in the processing queue and in the dead letter queue.
 * Read from RabbitMQ each time metrics are scraped. Anything in the dead letter queue needs a person.
 */
@Component
@ConditionalOnProperty(name = "dewtally.processing.async", havingValue = "true")
class QueueMetrics implements MeterBinder {

    private final AmqpAdmin admin;

    QueueMetrics(AmqpAdmin admin) {
        this.admin = admin;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        register(registry, ProcessingQueue.NAME, "received");
        register(registry, ProcessingQueue.DEAD_LETTER, "dead-letter");
    }

    private void register(MeterRegistry registry, String queue, String tag) {
        Gauge.builder("dewtally.queue.messages", () -> messageCount(queue))
                .description("Messages ready in the queue")
                .tag("queue", tag)
                .register(registry);
    }

    // NaN = unknown (RabbitMQ unreachable), shown as a gap instead of a misleading 0.
    private double messageCount(String queue) {
        try {
            QueueInformation info = admin.getQueueInfo(queue);
            return info == null ? Double.NaN : info.getMessageCount();
        } catch (AmqpException e) {
            return Double.NaN;
        }
    }
}