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

    // Returning normally acknowledges the message. Throwing (a temporary problem, or a payload that is
    // not a document id) makes Spring retry it; after the last retry it goes to the dead letter queue.
    @RabbitListener(queues = ProcessingQueue.NAME)
    void onDocumentReceived(String documentId) {
        processor.process(UUID.fromString(documentId));
    }
}