package com.kate.dewtally.invoice;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Read side of invoices. Converts entities to response objects inside the transaction,
 * so lazy collections are loaded here and never leak into the web layer (open-in-view is off).
 */
@Service
@Transactional(readOnly = true)
public class InvoiceQueryService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_QUERY_LENGTH = 100;

    private final InvoiceRepository invoices;

    public InvoiceQueryService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    // A search for "%" must find a literal "%", not everything: escape the LIKE wildcards.
    static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String text = query.strip().toLowerCase(Locale.ROOT);
        if (text.length() > MAX_QUERY_LENGTH) {
            text = text.substring(0, MAX_QUERY_LENGTH);
        }
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public Optional<InvoiceDetailsResponse> findById(UUID tenantId, UUID invoiceId) {
        return invoices.findByIdAndTenantId(invoiceId, tenantId)
                .map(InvoiceDetailsResponse::from);
    }

    /**
     * @param status optional filter
     * @param query  optional search in supplier name and invoice number, case-insensitive
     */
    public PageResponse<InvoiceSummaryResponse> list(UUID tenantId, InvoiceStatus status, String query,
                                                     int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return PageResponse.from(
                invoices.search(tenantId, status, likePattern(query), pageable),
                InvoiceSummaryResponse::from);
    }

    public InvoiceCountsResponse counts(UUID tenantId) {
        Map<InvoiceStatus, Long> byStatus = new EnumMap<>(InvoiceStatus.class);
        for (InvoiceStatus status : InvoiceStatus.values()) {
            byStatus.put(status, 0L);
        }
        invoices.countByStatus(tenantId).forEach(row -> byStatus.put(row.status(), row.count()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new InvoiceCountsResponse(byStatus, total);
    }
}