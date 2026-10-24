package com.kate.dewtally.supplier;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Public entry point of the supplier module: maintaining master data and looking suppliers up
 * for invoice validation.
 */
@Service
public class SupplierService {

    private static final int MAX_NAME_LENGTH = 300;
    private static final int MAX_VAT_ID_LENGTH = 30;

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    private static void requireValidIban(String iban) {
        if (!Iban.isValid(iban)) {
            throw badRequest("Invalid IBAN (checksum or format)");
        }
    }

    // "de 123 456 789" → "DE123456789"
    private static String normalizeVatId(String vatId) {
        if (vatId == null || vatId.isBlank()) {
            return null;
        }
        return vatId.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @Transactional
    public SupplierResponse create(UUID tenantId, String name, String vatId, String iban, String user) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty() || cleanName.length() > MAX_NAME_LENGTH) {
            throw badRequest("name is required (max " + MAX_NAME_LENGTH + " characters)");
        }
        String cleanVatId = normalizeVatId(vatId);
        if (cleanVatId != null && cleanVatId.length() > MAX_VAT_ID_LENGTH) {
            throw badRequest("vatId is too long");
        }
        if (cleanVatId != null && suppliers.existsByTenantIdAndVatId(tenantId, cleanVatId)) {
            throw new SupplierStateException("A supplier with VAT ID " + cleanVatId + " already exists");
        }

        Supplier supplier = new Supplier(tenantId, cleanName, cleanVatId, user);
        if (iban != null && !iban.isBlank()) {
            requireValidIban(iban);
            supplier.addIban(iban, user);
        }
        return SupplierResponse.from(suppliers.save(supplier));
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list(UUID tenantId) {
        return suppliers.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(SupplierResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID tenantId, UUID supplierId) {
        return SupplierResponse.from(load(tenantId, supplierId));
    }

    @Transactional
    public SupplierResponse addIban(UUID tenantId, UUID supplierId, String iban, String user) {
        requireValidIban(iban);
        Supplier supplier = load(tenantId, supplierId);
        supplier.addIban(iban, user);
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse verifyIban(UUID tenantId, UUID supplierId, String iban, String user) {
        Supplier supplier = load(tenantId, supplierId);
        supplier.verifyIban(iban, user);
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse block(UUID tenantId, UUID supplierId) {
        Supplier supplier = load(tenantId, supplierId);
        supplier.block();
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse unblock(UUID tenantId, UUID supplierId) {
        Supplier supplier = load(tenantId, supplierId);
        supplier.unblock();
        return SupplierResponse.from(supplier);
    }


    // Whether the tenant maintains supplier master data at all.
    @Transactional(readOnly = true)
    public boolean hasSuppliers(UUID tenantId) {
        return suppliers.existsByTenantId(tenantId);
    }
    
    @Transactional(readOnly = true)
    public Optional<SupplierCheck> findForInvoice(UUID tenantId, String vatId, String name) {
        String cleanVatId = normalizeVatId(vatId);
        Optional<Supplier> supplier = cleanVatId == null
                ? Optional.empty()
                : suppliers.findByTenantIdAndVatId(tenantId, cleanVatId);
        if (supplier.isEmpty() && name != null && !name.isBlank()) {
            supplier = suppliers.findFirstByTenantIdAndNameIgnoreCase(tenantId, name.strip());
        }
        return supplier.map(SupplierCheck::from);
    }

    private Supplier load(UUID tenantId, UUID supplierId) {
        return suppliers.findByIdAndTenantId(supplierId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
    }
}