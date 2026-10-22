package com.kate.dewtally.invoice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public entry point of the invoice module. Other modules create and look up invoices
 * through this service instead of using the repository directly.
 */
@Service
public class InvoiceService {

    private final InvoiceRepository invoices;

    public InvoiceService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    // One transaction: the invoice and all its lines are saved together or not at all.
    @Transactional
    public Invoice create(UUID tenantId,
                          UUID documentId,
                          ExtractionMethod extractionMethod,
                          InvoiceData data,
                          List<InvoiceLine> lines) {
        Invoice invoice = new Invoice(tenantId, documentId, extractionMethod, data);
        lines.forEach(invoice::addLine);
        return invoices.save(invoice);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findIdByDocument(UUID tenantId, UUID documentId) {
        return invoices.findByTenantIdAndDocumentId(tenantId, documentId).map(Invoice::getId);
    }
}