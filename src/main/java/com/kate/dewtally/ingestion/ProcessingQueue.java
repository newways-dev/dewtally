package com.kate.dewtally.ingestion;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The queue of uploaded documents waiting to be processed, and its dead letter queue for messages
 * that failed every retry. Only created in asynchronous mode, so synchronous runs (tests) never
 * connect to RabbitMQ.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "dewtally.processing.async", havingValue = "true")
class ProcessingQueue {

    static final String NAME = "dewtally.documents.received";
    static final String DEAD_LETTER = NAME + ".dlq";

    // Durable: the queue and its messages survive a RabbitMQ restart.
    // A rejected message is moved to the dead letter queue through the default exchange ("").
    @Bean
    Queue documentsReceivedQueue() {
        return QueueBuilder.durable(NAME)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER)
                .build();
    }

    // Quarantine: messages here are kept for inspection and can be moved back by hand.
    @Bean
    Queue documentsDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER).build();
    }
}