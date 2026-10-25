package com.kate.dewtally.document;

public enum ProcessingStatus {
    // Stored, waiting to be turned into an invoice
    RECEIVED,
    // Processing finished (an invoice exists, or the file has no invoice data)
    PROCESSED,
    // Processing failed; uploading the same file again retries
    FAILED
}