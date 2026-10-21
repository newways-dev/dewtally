package com.kate.dewtally.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    // Always scoped by tenant: a tenant must never be able to read another tenant's invoice.
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Invoice> findByTenantIdAndStatus(UUID tenantId, InvoiceStatus status);
}