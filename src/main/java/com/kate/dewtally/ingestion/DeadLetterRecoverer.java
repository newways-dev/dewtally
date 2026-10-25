package com.kate.dewtally.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Runs when a message failed every retry: marks the document FAILED, then rejects the message,
 * which RabbitMQ moves to the dead letter queue. Spring Boot uses this bean for listener retries.
 */
@Component
@ConditionalOnProperty(name = "dewtally.processing.async", havingValue = "true")
class DeadLetterRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRecoverer.class);

    private final DocumentProcessor processor;
    private final MessageRecoverer reject = new RejectAndDontRequeueRecoverer();

    DeadLetterRecoverer(DocumentProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        log.error("Giving up on message '{}' after all retries, moving it to the dead letter queue", payload, cause);
        try {
            processor.giveUp(UUID.fromString(payload));
        } catch (RuntimeException e) {
            // Not a document id (a poison message), or the database is still down: the message is kept anyway.
            log.warn("Could not mark the document of message '{}' as failed: {}", payload, e.toString());
        }
        reject.recover(message, cause);
    }
}