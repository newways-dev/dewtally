package com.kate.dewtally.review;

import java.time.Instant;

public record InvoiceChangeResponse(
        ChangeType changeType,
        String field,
        String oldValue,
        String newValue,
        String comment,
        String changedBy,
        Instant changedAt
) {

    static InvoiceChangeResponse from(InvoiceChange change) {
        return new InvoiceChangeResponse(
                change.getChangeType(),
                change.getField(),
                change.getOldValue(),
                change.getNewValue(),
                change.getComment(),
                change.getChangedBy(),
                change.getChangedAt());
    }
}