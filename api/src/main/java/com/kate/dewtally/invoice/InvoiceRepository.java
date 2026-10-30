package com.kate.dewtally.invoice;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Invoice> findByTenantIdAndDocumentId(UUID tenantId, UUID documentId);

    List<Invoice> findByTenantIdAndStatus(UUID tenantId, InvoiceStatus status);

    Page<Invoice> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Invoice> findByTenantIdAndStatus(UUID tenantId, InvoiceStatus status, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Invoice list with optional filters: status, and a search pattern on supplier name and invoice number.
     * The pattern is lower case, already wrapped in % and with % and _ escaped by the caller.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.tenantId = :tenantId
              AND (:status IS NULL OR i.status = :status)
              AND (:pattern IS NULL
                   OR LOWER(i.supplierName) LIKE :pattern ESCAPE '\\'
                   OR LOWER(i.invoiceNumber) LIKE :pattern ESCAPE '\\')
            """)
    Page<Invoice> search(@Param("tenantId") UUID tenantId,
                         @Param("status") InvoiceStatus status,
                         @Param("pattern") String pattern,
                         Pageable pageable);

    // One query for all counts instead of one per status
    @Query("""
            SELECT new com.kate.dewtally.invoice.StatusCount(i.status, COUNT(i))
            FROM Invoice i
            WHERE i.tenantId = :tenantId
            GROUP BY i.status
            """)
    List<StatusCount> countByStatus(@Param("tenantId") UUID tenantId);

    // Loads an invoice that is about to be changed by a person. The version is always increased,
    // even if only lines change, so a parallel change of the same invoice is always detected.
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.tenantId = :tenantId")
    Optional<Invoice> findForChange(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

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