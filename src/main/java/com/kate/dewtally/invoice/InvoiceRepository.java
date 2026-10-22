package com.kate.dewtally.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    // Always scoped by tenant: a tenant must never be able to read another tenant's invoice.
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Invoice> findByTenantIdAndDocumentId(UUID tenantId, UUID documentId);

    List<Invoice> findByTenantIdAndStatus(UUID tenantId, InvoiceStatus status);

    @Query("""
            SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END
            FROM Invoice i
            WHERE i.tenantId = :tenantId
              AND i.supplierVatId = :supplierVatId
              AND i.invoiceNumber = :invoiceNumber
              AND i.id <> :excludedId
            """)
    boolean existsOtherInvoice(@Param("tenantId") UUID tenantId,
                               @Param("supplierVatId") String supplierVatId,
                               @Param("invoiceNumber") String invoiceNumber,
                               @Param("excludedId") UUID excludedId);

    /**
     * Same check for invoices without a supplier VAT ID: compares the supplier name instead.
     */
    @Query("""
            SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END
            FROM Invoice i
            WHERE i.tenantId = :tenantId
              AND i.supplierName = :supplierName
              AND i.invoiceNumber = :invoiceNumber
              AND i.id <> :excludedId
            """)
    boolean existsOtherInvoiceBySupplierName(@Param("tenantId") UUID tenantId,
                                             @Param("supplierName") String supplierName,
                                             @Param("invoiceNumber") String invoiceNumber,
                                             @Param("excludedId") UUID excludedId);
}