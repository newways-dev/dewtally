package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;
import com.kate.dewtally.document.InvoiceDocumentRepository;
import com.kate.dewtally.format.DetectedFormat;
import com.kate.dewtally.format.FormatDetector;
import com.kate.dewtally.storage.DocumentStorage;
import com.kate.dewtally.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Accepts an uploaded file: hashes it, skips files the tenant already uploaded,
 * detects its format, stores the original and records it as an invoice document.
 * <p>
 * Deliberately not @Transactional: storing a file is not part of a database transaction,
 * and the database write below runs in its own short transaction.
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

    public DocumentUploadService(TenantRepository tenants,
                                 InvoiceDocumentRepository documents,
                                 DocumentStorage storage,
                                 FormatDetector formatDetector) {
        this.tenants = tenants;
        this.documents = documents;
        this.storage = storage;
        this.formatDetector = formatDetector;
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
        // Web-specific exceptions for now; replaced by domain exceptions + ProblemDetail in step 11.
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
            return new UploadResult(existing.get(), false);
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

        try {
            InvoiceDocument saved = documents.saveAndFlush(document);
            log.info("Tenant {} uploaded new document {} ({}, profile {})",
                    tenantId, saved.getId(), detected.format(), detected.profile());
            return new UploadResult(saved, true);
        } catch (DataIntegrityViolationException e) {
            // Race condition: a parallel request saved the same file a moment earlier.
            // The unique constraint in the database caught it; return the winner.
            // The file stored above becomes an orphan; a cleanup job can remove it later.
            return documents.findByTenantIdAndSha256(tenantId, sha256)
                    .map(winner -> new UploadResult(winner, false))
                    .orElseThrow(() -> e);
        }
    }
}