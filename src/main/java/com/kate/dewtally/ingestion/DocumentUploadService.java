package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;
import com.kate.dewtally.document.InvoiceDocumentRepository;
import com.kate.dewtally.document.ProcessingStatus;
import com.kate.dewtally.format.DetectedFormat;
import com.kate.dewtally.format.FormatDetector;
import com.kate.dewtally.invoice.InvoiceService;
import com.kate.dewtally.storage.DocumentStorage;
import com.kate.dewtally.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Accepts an uploaded file: hashes it, skips files the tenant already uploaded, detects its format,
 * stores the original and records it as an invoice document. Then the document is processed:
 * right away (synchronous mode) or in the background through RabbitMQ (asynchronous mode).
 * Deliberately not @Transactional: storing a file is not part of a database transaction.
 * If processing fails, the document (the legal original) is still kept; uploading the same file
 * again processes it again.
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
    private final DocumentProcessor processor;
    private final InvoiceService invoiceService;
    private final RabbitTemplate rabbit;
    private final boolean async;

    public DocumentUploadService(TenantRepository tenants,
                                 InvoiceDocumentRepository documents,
                                 DocumentStorage storage,
                                 FormatDetector formatDetector,
                                 DocumentProcessor processor,
                                 InvoiceService invoiceService,
                                 RabbitTemplate rabbit,
                                 @Value("${dewtally.processing.async:false}") boolean async) {
        this.tenants = tenants;
        this.documents = documents;
        this.storage = storage;
        this.formatDetector = formatDetector;
        this.processor = processor;
        this.invoiceService = invoiceService;
        this.rabbit = rabbit;
        this.async = async;
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
            return alreadyUploaded(existing.get(), content);
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
                    .map(winner -> alreadyUploaded(winner, content))
                    .orElseThrow(() -> e);
        }
        log.info("Tenant {} uploaded new document {} ({}, profile {})",
                tenantId, saved.getId(), detected.format(), detected.profile());

        return new UploadResult(saved, true, dispatch(saved, content));
    }

    /**
     * A document with its processing status and, once processed, its invoice.
     */
    DocumentResponse get(UUID tenantId, UUID documentId) {
        InvoiceDocument document = documents.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        UUID invoiceId = invoiceService.findIdByDocument(tenantId, documentId).orElse(null);
        return DocumentResponse.from(document, invoiceId);
    }

    private UploadResult alreadyUploaded(InvoiceDocument document, byte[] content) {
        Optional<UUID> invoiceId = invoiceService.findIdByDocument(document.getTenantId(), document.getId());
        if (invoiceId.isPresent()) {
            return new UploadResult(document, false, invoiceId.get());
        }
        // An earlier attempt kept the document but failed before the invoice existed: try again.
        log.info("Document {} has no invoice yet, processing it again", document.getId());
        return new UploadResult(document, false, dispatch(document, content));
    }

    // Synchronous: process now and return the invoice id. Asynchronous: queue the document and return null.
    private UUID dispatch(InvoiceDocument document, byte[] content) {
        if (!async) {
            return processor.process(document, content);
        }
        if (document.getProcessingStatus() != ProcessingStatus.RECEIVED) {
            document.markReceived();
            documents.save(document);
        }
        try {
            // Only the id goes into the queue; the file stays in storage. The document is already
            // committed (saveAndFlush), so the listener is guaranteed to find it.
            rabbit.convertAndSend(ProcessingQueue.NAME, document.getId().toString());
            log.info("Queued document {} for processing", document.getId());
        } catch (AmqpException e) {
            // Known gap, closed by the transactional outbox (layer 4, step 2): the document stays
            // RECEIVED, and uploading the same file again queues it again.
            log.error("Could not queue document {} for processing", document.getId(), e);
        }
        return null;
    }
}