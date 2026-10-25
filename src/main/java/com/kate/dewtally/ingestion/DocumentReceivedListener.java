package com.kate.dewtally.ingestion;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Takes documents from the queue and processes them in the background.
 */
@Component
// Only where documents are processed: the worker, or the all-in-one app during development.
@ConditionalOnProperty(name = {"dewtally.processing.async", "dewtally.processing.listener-enabled"}, havingValue = "true")
class DocumentReceivedListener {

    private final DocumentProcessor processor;
    private final MeterRegistry metrics;

    DocumentReceivedListener(DocumentProcessor processor, MeterRegistry metrics) {
        this.processor = processor;
        this.metrics = metrics;
    }

    // Returning normally acknowledges the message. Throwing (a temporary problem, or a payload that is
    // not a document id) makes Spring retry it; after the last retry it goes to the dead letter queue.
    @RabbitListener(queues = ProcessingQueue.NAME)
    void onDocumentReceived(String documentId) {
        Timer.Sample sample = Timer.start(metrics);
        String outcome = "error"; // thrown: will be retried or end in the dead letter queue
        try {
            UUID invoiceId = processor.process(UUID.fromString(documentId));
            outcome = invoiceId != null ? "invoice" : "no-invoice";
        } finally {
            sample.stop(Timer.builder("dewtally.documents.processing")
                    .description("Time to process one document from the queue")
                    .tag("outcome", outcome)
                    .register(metrics));
        }
    }
}