package com.kate.dewtally.supplier;

import com.kate.dewtally.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Supplier master data. Company and user name come from the token: the four-eyes check on IBANs
 * compares real, logged-in people.
 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService suppliers;

    public SupplierController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(CurrentUser user, @RequestBody CreateSupplierRequest request) {
        SupplierResponse created = suppliers.create(
                user.tenantId(), request.name(), request.vatId(), request.iban(), user.name());
        return ResponseEntity.created(URI.create("/api/suppliers/" + created.id())).body(created);
    }

    @GetMapping
    public List<SupplierResponse> list(CurrentUser user) {
        return suppliers.list(user.tenantId());
    }

    @GetMapping("/{id}")
    public SupplierResponse get(CurrentUser user, @PathVariable UUID id) {
        return suppliers.get(user.tenantId(), id);
    }

    @PostMapping("/{id}/ibans")
    public SupplierResponse addIban(CurrentUser user, @PathVariable UUID id, @RequestBody AddIbanRequest request) {
        if (request == null || request.iban() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "iban is required");
        }
        return suppliers.addIban(user.tenantId(), id, request.iban(), user.name());
    }

    // The second person confirms the IBAN, e.g. after calling the supplier on a known phone number.
    @PostMapping("/{id}/ibans/{iban}/verify")
    public SupplierResponse verifyIban(CurrentUser user, @PathVariable UUID id, @PathVariable String iban) {
        return suppliers.verifyIban(user.tenantId(), id, iban, user.name());
    }

    @PostMapping("/{id}/block")
    public SupplierResponse block(CurrentUser user, @PathVariable UUID id) {
        return suppliers.block(user.tenantId(), id);
    }

    @PostMapping("/{id}/unblock")
    public SupplierResponse unblock(CurrentUser user, @PathVariable UUID id) {
        return suppliers.unblock(user.tenantId(), id);
    }
}