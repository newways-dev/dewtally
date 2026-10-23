package com.kate.dewtally.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Read side of invoices. Converts entities to response objects inside the transaction,
 * so lazy collections are loaded here and never leak into the web layer (open-in-view is off).
 */
@Service
@Transactional(readOnly = true)
public class InvoiceQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final InvoiceRepository invoices;

    public InvoiceQueryService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    public Optional<InvoiceDetailsResponse> findById(UUID tenantId, UUID invoiceId) {
        return invoices.findByIdAndTenantId(invoiceId, tenantId)
                .map(InvoiceDetailsResponse::from);
    }

    public PageResponse<InvoiceSummaryResponse> list(UUID tenantId, InvoiceStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Invoice> result = status == null
                ? invoices.findByTenantId(tenantId, pageable)
                : invoices.findByTenantIdAndStatus(tenantId, status, pageable);

        return PageResponse.from(result, InvoiceSummaryResponse::from);
    }
}