package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;
import com.kate.dewtally.document.InvoiceDocumentRepository;
import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.extraction.*;
import com.kate.dewtally.invoice.Invoice;
import com.kate.dewtally.invoice.InvoiceService;
import com.kate.dewtally.parsing.InvoiceParser;
import com.kate.dewtally.parsing.ParsedInvoice;
import com.kate.dewtally.storage.DocumentStorage;
import com.kate.dewtally.storage.StorageException;
import com.kate.dewtally.tenant.TenantContext;
import com.kate.dewtally.xml.InvalidXmlException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a stored document into an invoice: from the XML for structured e-invoices, with a language model
 * for plain PDFs. Called during the upload (synchronous mode) or by the queue listener (asynchronous mode).
 * Permanent problems (broken file, unreadable PDF, model failure) are handled here: the document ends up
 * PROCESSED (possibly with an invoice for manual review) or FAILED. Temporary problems in the queue path
 * are thrown as {@link TransientProcessingException}, so the message is retried.
 * Deliberately not @Transactional: the model call takes seconds, during which no database connection
 * should be held.
 */
@Service
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final InvoiceDocumentRepository documents;
    private final DocumentTenants tenants;
    private final DocumentStorage storage;
    private final InvoiceParser invoiceParser;
    private final PdfTextExtractor pdfTextExtractor;
    private final LlmInvoiceExtractor llmExtractor;
    private final InvoiceService invoiceService;
    private final InvoiceImporter importer;
    private final Duration pdfTimeout;
    // PDF parsing runs on untrusted files in its own small pool, separate from everything else.
    private final Bulkhead pdfBulkhead;

    public DocumentProcessor(InvoiceDocumentRepository documents,
                             DocumentTenants tenants,
                             DocumentStorage storage,
                             InvoiceParser invoiceParser,
                             PdfTextExtractor pdfTextExtractor,
                             LlmInvoiceExtractor llmExtractor,
                             InvoiceService invoiceService,
                             InvoiceImporter importer,
                             @Value("${dewtally.extraction.pdf-timeout:20s}") Duration pdfTimeout,
                             @Value("${dewtally.extraction.pdf-max-concurrent:2}") int pdfMaxConcurrent,
                             @Value("${dewtally.extraction.pdf-max-queued:10}") int pdfMaxQueued) {
        this.documents = documents;
        this.tenants = tenants;
        this.storage = storage;
        this.invoiceParser = invoiceParser;
        this.pdfTextExtractor = pdfTextExtractor;
        this.llmExtractor = llmExtractor;
        this.invoiceService = invoiceService;
        this.importer = importer;
        this.pdfTimeout = pdfTimeout;
        this.pdfBulkhead = new Bulkhead("pdf-parsing", pdfMaxConcurrent, pdfMaxQueued);
    }

    // Problems that will probably go away on their own: worth a retry.
    static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof BulkheadFullException
                    || t instanceof TransientDataAccessException
                    || t instanceof DataAccessResourceFailureException
                    || t instanceof CannotCreateTransactionException) {
                return true;
            }
        }
        return false;
    }

    @PreDestroy
    void shutdown() {
        pdfBulkhead.close();
    }

    /**
     * Entry point for the queue: finds the document's tenant, then loads the document and its file
     * as that tenant. Safe to call twice for the same document: a message can be delivered more than once.
     *
     * @throws TransientProcessingException for temporary problems; the message is then retried
     */
    public UUID process(UUID documentId) {
        Optional<UUID> tenantId = tenants.find(documentId);
        if (tenantId.isEmpty()) {
            log.warn("Document {} not found, nothing to process", documentId);
            return null;
        }
        return TenantContext.callAs(tenantId.get(), () -> processAsTenant(documentId));
    }

    private UUID processAsTenant(UUID documentId) {
        Optional<InvoiceDocument> found = documents.findById(documentId);
        if (found.isEmpty()) {
            log.warn("Document {} not found, nothing to process", documentId);
            return null;
        }
        InvoiceDocument document = found.get();

        Optional<UUID> existing = invoiceService.findIdByDocument(document.getTenantId(), document.getId());
        if (existing.isPresent()) {
            log.info("Document {} already has invoice {}, skipping", documentId, existing.get());
            finish(document, true);
            return existing.get();
        }

        byte[] content;
        try {
            content = storage.load(document.getStorageKey());
        } catch (StorageException e) {
            log.error("Could not load the file of document {}", documentId, e);
            finish(document, false);
            return null;
        }
        return run(document, content, true);
    }

    /**
     * Processes a document whose content is already in memory (the upload request, which already
     * runs as the user's tenant). Never throws.
     */
    UUID process(InvoiceDocument document, byte[] content) {
        return run(document, content, false);
    }

    /**
     * Called when every retry has failed: the document is marked FAILED, unless an invoice
     * was created in the meantime. Uploading the same file again starts over.
     */
    public void giveUp(UUID documentId) {
        Optional<UUID> tenantId = tenants.find(documentId);
        if (tenantId.isEmpty()) {
            log.warn("Document {} not found, nothing to mark as failed", documentId);
            return;
        }
        TenantContext.runAs(tenantId.get(), () -> documents.findById(documentId).ifPresent(document -> {
            boolean hasInvoice = invoiceService.findIdByDocument(document.getTenantId(), documentId).isPresent();
            finish(document, hasInvoice);
        }));
    }

    /**
     * @param retryable true in the queue path: temporary problems are thrown so the message is retried;
     *                  false during the upload request, where there is nobody to retry
     */
    private UUID run(InvoiceDocument document, byte[] content, boolean retryable) {
        try {
            UUID invoiceId = document.getSourceFormat() == SourceFormat.PDF
                    ? importPdfInvoice(document, content)
                    : importStructuredInvoice(document, content);
            finish(document, true);
            return invoiceId;
        } catch (RuntimeException e) {
            if (retryable && isTransient(e)) {
                log.warn("Temporary problem with document {}, it will be retried: {}", document.getId(), e.toString());
                throw new TransientProcessingException(document.getId(), e);
            }
            log.error("Processing of document {} failed; uploading the file again retries", document.getId(), e);
            // A parallel attempt may have succeeded in the meantime.
            Optional<UUID> existing = invoiceService.findIdByDocument(document.getTenantId(), document.getId());
            finish(document, existing.isPresent());
            return existing.orElse(null);
        }
    }

    private void finish(InvoiceDocument document, boolean success) {
        if (success) {
            document.markProcessed();
        } else {
            document.markFailed();
        }
        documents.save(document);
    }

    // Reads invoice data from structured formats. Unreadable XML keeps the document without an invoice.
    private UUID importStructuredInvoice(InvoiceDocument document, byte[] content) {
        Optional<ParsedInvoice> parsed;
        try {
            parsed = invoiceParser.parse(document.getSourceFormat(), content);
        } catch (InvalidXmlException | UncheckedIOException e) {
            log.warn("Could not read invoice data from document {}: {}", document.getId(), e.getMessage());
            return null;
        }
        if (parsed.isEmpty()) {
            return null; // unknown file: no structured data to read
        }
        return importer.importStructured(document, parsed.get(), content);
    }

    // Plain PDF: text → language model → same rules as for XML.
    // Whatever goes wrong with the file or the model, an invoice is created and sent to review.
    private UUID importPdfInvoice(InvoiceDocument document, byte[] content) {
        PdfText text;
        try {
            text = pdfBulkhead.call(() -> pdfTextExtractor.extract(content), pdfTimeout);
        } catch (BulkheadFullException e) {
            // Server is busy, not a problem of the file: retried in the queue path.
            log.warn("PDF parsing is at capacity, document {} is not processed now", document.getId());
            throw e;
        } catch (PdfReadException | TimeLimitExceededException e) {
            log.warn("Could not read PDF document {}: {}", document.getId(), e.getMessage());
            return forReview(document, "DT-PDF-UNREADABLE",
                    "Die PDF-Datei konnte nicht gelesen werden. Bitte manuell erfassen.");
        }

        if (!text.hasTextLayer()) {
            return forReview(document, "DT-NO-TEXT-LAYER",
                    "Die PDF-Datei enthält keinen Text (vermutlich ein Scan). Bitte manuell erfassen.");
        }

        InvoiceExtraction extraction;
        try {
            extraction = llmExtractor.extract(text.text());
        } catch (RuntimeException e) {
            // Network, API key, quota, timeout, unexpected answer: the cause goes to the log, the invoice to a person.
            log.error("LLM extraction failed for document {}", document.getId(), e);
            return forReview(document, "DT-EXTRACTION-FAILED",
                    "Die automatische Erfassung ist fehlgeschlagen. Bitte manuell erfassen.");
        }

        return importer.importExtracted(document, extraction, content);
    }

    private UUID forReview(InvoiceDocument document, String ruleId, String message) {
        Invoice invoice = invoiceService.createForReview(document.getTenantId(), document.getId(), ruleId, message);
        log.info("Created invoice {} for manual review from document {}: {}", invoice.getId(), document.getId(), ruleId);
        return invoice.getId();
    }
}