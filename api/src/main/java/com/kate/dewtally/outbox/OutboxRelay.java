package com.kate.dewtally.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Sends recorded messages to RabbitMQ. Delivery is at-least-once: if the send succeeds but marking
 * the message as sent fails, it is sent again, so consumers must be idempotent.
 */
@Component
// Only where uploads are written: the API, or the all-in-one app during development.
@ConditionalOnProperty(name = {"dewtally.processing.async", "dewtally.outbox.relay-enabled"}, havingValue = "true")
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository repository;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;

    OutboxRelay(OutboxRepository repository, RabbitTemplate rabbit, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.rabbit = rabbit;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    // fixedDelay: the next run starts only after the previous one has finished.
    @Scheduled(fixedDelayString = "${dewtally.outbox.poll-interval-ms:1000}")
    void relay() {
        Integer sent;
        do {
            sent = transactions.execute(status -> sendBatch());
        } while (sent != null && sent == BATCH_SIZE); // a full batch: there may be more waiting
    }

    // One transaction per batch: the row locks are held until the batch is marked.
    private int sendBatch() {
        List<OutboxMessage> batch = repository.lockUnsent(BATCH_SIZE);
        int sent = 0;
        for (OutboxMessage message : batch) {
            try {
                rabbit.convertAndSend(message.getDestination(), message.getPayload());
                message.markSent();
                sent++;
            } catch (AmqpException e) {
                // Most likely RabbitMQ is down: stop here, the rest waits for the next run.
                message.recordFailure(e.getMessage());
                log.warn("Could not send outbox message {} (attempt {}): {}",
                        message.getId(), message.getAttempts(), e.getMessage());
                break;
            }
        }
        return sent;
    }
}