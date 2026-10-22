package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;
import com.kate.dewtally.document.SourceFormat;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API returns for an uploaded document.
 * The storage key is intentionally not exposed: it is an internal detail.
 */
public record DocumentResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        SourceFormat sourceFormat,
        Instant uploadedAt
) {

    static DocumentResponse from(InvoiceDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getSha256(),
                document.getSourceFormat(),
                document.getUploadedAt());
    }
}