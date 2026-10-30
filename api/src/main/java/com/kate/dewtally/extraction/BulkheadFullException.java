package com.kate.dewtally.extraction;

/**
 * All workers of a bulkhead are busy and its queue is full: the task was not started.
 */
public class BulkheadFullException extends RuntimeException {

    public BulkheadFullException(String message) {
        super(message);
    }
}