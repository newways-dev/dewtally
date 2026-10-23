package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;
import com.kate.dewtally.document.InvoiceDocumentRepository;
import com.kate.dewtally.document.SourceFormat;
import com.kate.dewtally.extraction.*;
import com.kate.dewtally.format.DetectedFormat;
import com.kate.dewtally.format.FormatDetector;
import com.kate.dewtally.invoice.*;
import com.kate.dewtally.parsing.InvoiceParser;
import com.kate.dewtally.parsing.ParsedInvoice;
import com.kate.dewtally.parsing.ParsedLine;
import com.kate.dewtally.storage.DocumentStorage;
import com.kate.dewtally.tenant.TenantRepository;
import com.kate.dewtally.validation.InvoiceValidationService;
import com.kate.dewtally.xml.InvalidXmlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Accepts an uploaded file: hashes it, skips files the tenant already uploaded,
 * detects its format, stores the original, records it as an invoice document and
 * creates the invoice: from the XML for structured e-invoices, with a language model for plain PDFs.
 * <p>
 * Deliberately not @Transactional: storing a file is not part of a database transaction,
 * and the model call takes seconds, during which no database connection should be held.
 * Each database write below runs in its own short transaction.
 */
@Service
public class DocumentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);

    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_CONTENT_TYPE_LENGTH = 100;

    private final TenantRepository tenants;
    private final InvoiceDocumentRepository documents;
    private final DocumentStorage storage;
    private final FormatDetector formatDetector;
    private final InvoiceParser invoiceParser;
    private final PdfTextExtractor pdfTextExtractor;
    private final LlmInvoiceExtractor llmExtractor;
    private final InvoiceService invoiceService;
    private final InvoiceValidationService validationService;

    public DocumentUploadService(TenantRepository tenants,
                                 InvoiceDocumentRepository documents,
                                 DocumentStorage storage,
                                 FormatDetector formatDetector,
                                 InvoiceParser invoiceParser,
                                 PdfTextExtractor pdfTextExtractor,
                                 LlmInvoiceExtractor llmExtractor,
                                 InvoiceService invoiceService,
                                 InvoiceValidationService validationService) {
        this.tenants = tenants;
        this.documents = documents;
        this.storage = storage;
        this.formatDetector = formatDetector;
        this.invoiceParser = invoiceParser;
        this.pdfTextExtractor = pdfTextExtractor;
        this.llmExtractor = llmExtractor;
        this.invoiceService = invoiceService;
        this.validationService = validationService;
    }

    private static List<InvoiceLine> toInvoiceLines(ParsedInvoice parsed) {
        return parsed.lines().stream()
                .map(DocumentUploadService::toInvoiceLine)
                .toList();
    }

    private static InvoiceLine toInvoiceLine(ParsedLine line) {
        return new InvoiceLine(
                line.lineNo(),
                line.description(),
                line.quantity(),
                line.unitCode(),
                line.unitPrice(),
                line.netAmount(),
                line.taxRate());
    }

    private static byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }

    // Browsers may send a full path ("C:\\Users\\...\\rechnung.xml"); keep only the file name.
    private static String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "unnamed";
        }
        String name = originalName.substring(
                Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\')) + 1);
        if (name.isBlank()) {
            return "unnamed";
        }
        return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(0, MAX_FILE_NAME_LENGTH) : name;
    }

    private static String safeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.length() > MAX_CONTENT_TYPE_LENGTH
                ? contentType.substring(0, MAX_CONTENT_TYPE_LENGTH)
                : contentType;
    }

    UploadResult upload(UUID tenantId, MultipartFile file) {
        if (!tenants.existsById(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant: " + tenantId);
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }

        byte[] content = readContent(file);
        String sha256 = Sha256.hex(content);

        // Idempotency: the same file uploaded twice by one tenant is the same document.
        Optional<InvoiceDocument> existing = documents.findByTenantIdAndSha256(tenantId, sha256);
        if (existing.isPresent()) {
            log.info("Tenant {} uploaded an already known file, returning document {}",
                    tenantId, existing.get().getId());
            return alreadyUploaded(existing.get());
        }

        DetectedFormat detected = formatDetector.detect(content);

        // The original is stored before the database record, so it is never lost.
        String storageKey = storage.store(tenantId, content);
        InvoiceDocument document = new InvoiceDocument(
                tenantId,
                safeFileName(file.getOriginalFilename()),
                safeContentType(file.getContentType()),
                content.length,
                sha256,
                storageKey,
                detected.format(),
                detected.profile());

        InvoiceDocument saved;
        try {
            saved = documents.saveAndFlush(document);
        } catch (DataIntegrityViolationException e) {
            // Race condition: a parallel request saved the same file a moment earlier.
            // The unique constraint in the database caught it; return the winner.
            // The file stored above becomes an orphan; a cleanup job can remove it later.
            return documents.findByTenantIdAndSha256(tenantId, sha256)
                    .map(this::alreadyUploaded)
                    .orElseThrow(() -> e);
        }
        log.info("Tenant {} uploaded new document {} ({}, profile {})",
                tenantId, saved.getId(), detected.format(), detected.profile());

        UUID invoiceId = saved.getSourceFormat() == SourceFormat.PDF
                ? importPdfInvoice(saved, content)
                : importStructuredInvoice(saved, content);
        return new UploadResult(saved, true, invoiceId);
    }

    private UploadResult alreadyUploaded(InvoiceDocument document) {
        UUID invoiceId = invoiceService.findIdByDocument(document.getTenantId(), document.getId()).orElse(null);
        return new UploadResult(document, false, invoiceId);
    }

    // Reads invoice data from structured formats. If the data cannot be read, the document
    // is still kept (it is the legal original); it simply has no invoice yet.
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

        Invoice invoice = invoiceService.create(
                document.getTenantId(),
                document.getId(),
                ExtractionMethod.STRUCTURED,
                parsed.get().data(),
                toInvoiceLines(parsed.get()));
        return validate(document, invoice, content);
    }

    // Plain PDF: text → language model → same rules as for XML.
    // Whatever goes wrong, an invoice is created and sent to review with the reason.
    private UUID importPdfInvoice(InvoiceDocument document, byte[] content) {
        PdfText text;
        try {
            text = pdfTextExtractor.extract(content);
        } catch (PdfReadException e) {
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
            // Network, API key, quota, unexpected answer: the cause goes to the log, the invoice to a person.
            log.error("LLM extraction failed for document {}", document.getId(), e);
            return forReview(document, "DT-EXTRACTION-FAILED",
                    "Die automatische Erfassung ist fehlgeschlagen. Bitte manuell erfassen.");
        }

        Invoice invoice = invoiceService.createExtracted(
                document.getTenantId(),
                document.getId(),
                extraction.invoice().data(),
                toInvoiceLines(extraction.invoice()),
                extraction.confidence());
        return validate(document, invoice, content);
    }

    private UUID validate(InvoiceDocument document, Invoice invoice, byte[] content) {
        InvoiceStatus status = validationService.validate(
                document.getTenantId(), invoice.getId(), document.getSourceFormat(), document.getProfile(), content);
        log.info("Created invoice {} ({}) from document {}, status {}",
                invoice.getId(), invoice.getExtractionMethod(), document.getId(), status);
        return invoice.getId();
    }

    private UUID forReview(InvoiceDocument document, String ruleId, String message) {
        Invoice invoice = invoiceService.createForReview(document.getTenantId(), document.getId(), ruleId, message);
        log.info("Created invoice {} for manual review from document {}: {}", invoice.getId(), document.getId(), ruleId);
        return invoice.getId();
    }
}