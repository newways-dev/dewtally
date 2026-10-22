package com.kate.dewtally.storage;

/**
 * Thrown when a document cannot be stored or read.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}