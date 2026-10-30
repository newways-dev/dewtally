package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;

import java.util.UUID;

/**
 * Outcome of an upload: the stored document, whether it was newly created
 * (false = the tenant had already uploaded exactly this file) and the invoice
 * read from it (null for files without structured data, e.g. a plain PDF).
 */
record UploadResult(InvoiceDocument document, boolean created, UUID invoiceId) {
}