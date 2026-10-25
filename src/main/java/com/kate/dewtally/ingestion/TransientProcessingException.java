package com.kate.dewtally.ingestion;

import java.util.UUID;

/**
 * Processing failed for a reason that will probably go away (busy server, database briefly unavailable).
 * Thrown to the queue listener so the message is retried, instead of marking the document FAILED.
 */
public class TransientProcessingException extends RuntimeException {

    public TransientProcessingException(UUID documentId, Throwable cause) {
        super("Temporary problem while processing document " + documentId, cause);
    }
}