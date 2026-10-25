package com.kate.dewtally.ingestion;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The queue of uploaded documents waiting to be processed. Only created in asynchronous mode,
 * so synchronous runs (tests) never connect to RabbitMQ.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "dewtally.processing.async", havingValue = "true")
class ProcessingQueue {

    static final String NAME = "dewtally.documents.received";

    // Durable: the queue and its messages survive a RabbitMQ restart.
    @Bean
    Queue documentsReceivedQueue() {
        return QueueBuilder.durable(NAME).build();
    }
}