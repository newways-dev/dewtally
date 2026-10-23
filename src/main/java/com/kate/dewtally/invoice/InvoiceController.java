package com.kate.dewtally.invoice;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceQueryService queries;

    public InvoiceController(InvoiceQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{id}")
    public InvoiceDetailsResponse getById(@RequestHeader("X-Tenant-Id") UUID tenantId,
                                          @PathVariable UUID id) {
        return queries.findById(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    @GetMapping
    public PageResponse<InvoiceSummaryResponse> list(@RequestHeader("X-Tenant-Id") UUID tenantId,
                                                     @RequestParam(required = false) InvoiceStatus status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return queries.list(tenantId, status, page, size);
    }
}