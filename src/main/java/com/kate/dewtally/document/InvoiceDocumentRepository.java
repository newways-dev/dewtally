package com.kate.dewtally.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceDocumentRepository extends JpaRepository<InvoiceDocument, UUID> {

    // Used for idempotent uploads: if this tenant already uploaded the same file, reuse it.
    Optional<InvoiceDocument> findByTenantIdAndSha256(UUID tenantId, String sha256);

    // Tenant check on every read: a document of another tenant does not exist for the caller.
    Optional<InvoiceDocument> findByIdAndTenantId(UUID id, UUID tenantId);
}