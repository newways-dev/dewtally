package com.kate.dewtally.supplier;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    // Loads suppliers with their IBANs in one query instead of one extra query per supplier (N+1).
    @EntityGraph(attributePaths = "ibans")
    List<Supplier> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<Supplier> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Supplier> findByTenantIdAndVatId(UUID tenantId, String vatId);

    Optional<Supplier> findFirstByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantId(UUID tenantId);

    boolean existsByTenantIdAndVatId(UUID tenantId, String vatId);
}