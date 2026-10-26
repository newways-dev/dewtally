package com.kate.dewtally.invoice;

import com.kate.dewtally.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Reading invoices. Only invoices of the user's own company exist for them.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceQueryService queries;

    public InvoiceController(InvoiceQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{id}")
    public InvoiceDetailsResponse getById(CurrentUser user, @PathVariable UUID id) {
        return queries.findById(user.tenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    @GetMapping
    public PageResponse<InvoiceSummaryResponse> list(CurrentUser user,
                                                     @RequestParam(required = false) InvoiceStatus status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return queries.list(user.tenantId(), status, page, size);
    }
}