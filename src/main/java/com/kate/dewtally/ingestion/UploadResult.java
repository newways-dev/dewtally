package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;

/**
 * Outcome of an upload: the stored document and whether it was newly created
 * (false = the tenant had already uploaded exactly this file).
 */
record UploadResult(InvoiceDocument document, boolean created) {
}