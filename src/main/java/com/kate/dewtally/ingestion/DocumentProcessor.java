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
import com.kate.dewtally.xml.InvalidXmlException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a stored document into an invoice: from the XML for structured e-invoices, with a language model
 * for plain PDFs. Called during the upload (synchronous mode) or by the queue listener (asynchronous mode).
 * Never throws: the outcome is the document's processing status.
 * Deliberately not @Transactional: the model call takes seconds, during which no database connection
 * should be held.
 */
@Service
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final InvoiceDocumentRepository documents;
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
        this.storage = storage;
        this.invoiceParser = invoiceParser;
        this.pdfTextExtractor = pdfTextExtractor;
        this.llmExtractor = llmExtractor;
        this.invoiceService = invoiceService;
        this.importer = importer;
        this.pdfTimeout = pdfTimeout;
        this.pdfBulkhead = new Bulkhead("pdf-parsing", pdfMaxConcurrent, pdfMaxQueued);
    }

    @PreDestroy
    void shutdown() {
        pdfBulkhead.close();
    }

    /**
     * Entry point for the queue: loads the document and its file.
     * Safe to call twice for the same document: a message can be delivered more than once.
     */
    public UUID process(UUID documentId) {
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
        return process(document, content);
    }

    /**
     * Processes a document whose content is already in memory (the upload request).
     */
    UUID process(InvoiceDocument document, byte[] content) {
        try {
            UUID invoiceId = document.getSourceFormat() == SourceFormat.PDF
                    ? importPdfInvoice(document, content)
                    : importStructuredInvoice(document, content);
            finish(document, true);
            return invoiceId;
        } catch (RuntimeException e) {
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
            // Server is busy, not a problem of the file: the document is marked FAILED and can be retried.
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