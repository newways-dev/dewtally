package com.kate.dewtally.supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Supplier master data. Tenant and user come from headers until login exists (layer 5).
 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String USER_HEADER = "X-User-Name";

    private final SupplierService suppliers;

    public SupplierController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                                   @RequestHeader(value = USER_HEADER, defaultValue = "unknown") String user,
                                                   @RequestBody CreateSupplierRequest request) {
        SupplierResponse created = suppliers.create(tenantId, request.name(), request.vatId(), request.iban(), user);
        return ResponseEntity.created(URI.create("/api/suppliers/" + created.id())).body(created);
    }

    @GetMapping
    public List<SupplierResponse> list(@RequestHeader(TENANT_HEADER) UUID tenantId) {
        return suppliers.list(tenantId);
    }

    @GetMapping("/{id}")
    public SupplierResponse get(@RequestHeader(TENANT_HEADER) UUID tenantId, @PathVariable UUID id) {
        return suppliers.get(tenantId, id);
    }

    @PostMapping("/{id}/ibans")
    public SupplierResponse addIban(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                    @RequestHeader(value = USER_HEADER, defaultValue = "unknown") String user,
                                    @PathVariable UUID id,
                                    @RequestBody AddIbanRequest request) {
        if (request == null || request.iban() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "iban is required");
        }
        return suppliers.addIban(tenantId, id, request.iban(), user);
    }

    // The second person confirms the IBAN, e.g. after calling the supplier on a known phone number.
    @PostMapping("/{id}/ibans/{iban}/verify")
    public SupplierResponse verifyIban(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                       @RequestHeader(value = USER_HEADER, defaultValue = "unknown") String user,
                                       @PathVariable UUID id,
                                       @PathVariable String iban) {
        return suppliers.verifyIban(tenantId, id, iban, user);
    }

    @PostMapping("/{id}/block")
    public SupplierResponse block(@RequestHeader(TENANT_HEADER) UUID tenantId, @PathVariable UUID id) {
        return suppliers.block(tenantId, id);
    }

    @PostMapping("/{id}/unblock")
    public SupplierResponse unblock(@RequestHeader(TENANT_HEADER) UUID tenantId, @PathVariable UUID id) {
        return suppliers.unblock(tenantId, id);
    }
}