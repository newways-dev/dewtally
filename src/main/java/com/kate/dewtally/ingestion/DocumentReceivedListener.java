package com.kate.dewtally.ingestion;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Takes documents from the queue and processes them in the background.
 */
@Component
@ConditionalOnProperty(name = "dewtally.processing.async", havingValue = "true")
class DocumentReceivedListener {

    private final DocumentProcessor processor;

    DocumentReceivedListener(DocumentProcessor processor) {
        this.processor = processor;
    }

    // The processor never throws, so every message is acknowledged; failures end up as FAILED documents.
    // Retries and a dead letter queue follow in layer 4, step 3.
    @RabbitListener(queues = ProcessingQueue.NAME)
    void onDocumentReceived(String documentId) {
        processor.process(UUID.fromString(documentId));
    }
}