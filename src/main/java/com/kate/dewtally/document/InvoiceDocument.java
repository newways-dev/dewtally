package com.kate.dewtally.document;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded file exactly as received. Never modified after upload:
 * parsed and corrected data lives in the invoice, not here.
 */
@Entity
@Table(name = "invoice_document")
public class InvoiceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Stored as a plain id, not a @ManyToOne: the tenant is a security boundary
    // used for filtering, we never need to navigate from a document to its tenant.
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    // Content hash: the same file uploaded twice by one tenant is the same document.
    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 500)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false, updatable = false, length = 30)
    private SourceFormat sourceFormat;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected InvoiceDocument() {
        // Required by JPA.
    }

    public InvoiceDocument(UUID tenantId,
                           String fileName,
                           String contentType,
                           long sizeBytes,
                           String sha256,
                           String storageKey,
                           SourceFormat sourceFormat) {
        this.tenantId = tenantId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.sourceFormat = sourceFormat;
    }

    @PrePersist
    void onCreate() {
        this.uploadedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public SourceFormat getSourceFormat() {
        return sourceFormat;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}