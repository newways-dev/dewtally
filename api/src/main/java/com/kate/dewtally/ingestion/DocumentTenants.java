package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * The worker only gets a document id from the queue. The database function document_tenant tells
 * which tenant the document belongs to (and nothing else), so processing can run as that tenant.
 */
interface DocumentTenants extends Repository<InvoiceDocument, UUID> {

    @Query(value = "SELECT document_tenant(:documentId)", nativeQuery = true)
    Optional<UUID> find(@Param("documentId") UUID documentId);
}