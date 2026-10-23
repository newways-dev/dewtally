package com.kate.dewtally.invoice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    // Models occasionally return values outside 0..1; keep what we store meaningful.
    private static BigDecimal toConfidence(double value) {
        if (Double.isNaN(value)) {
            return null;
        }
        double clamped = Math.clamp(value, 0.0, 1.0);
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
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

    /**
     * Invoice whose data a language model read from a PDF.
     */
    @Transactional
    public Invoice createExtracted(UUID tenantId,
                                   UUID documentId,
                                   InvoiceData data,
                                   List<InvoiceLine> lines,
                                   double confidence) {
        Invoice invoice = new Invoice(tenantId, documentId, ExtractionMethod.LLM, data);
        invoice.recordExtractionConfidence(toConfidence(confidence));
        lines.forEach(invoice::addLine);
        return invoices.save(invoice);
    }

    /**
     * Invoice without data for a document that could not be read automatically (scan, broken PDF,
     * model unavailable). It goes straight to review with the reason, so no upload is ever lost.
     */
    @Transactional
    public Invoice createForReview(UUID tenantId, UUID documentId, String ruleId, String message) {
        Invoice invoice = new Invoice(tenantId, documentId, ExtractionMethod.LLM, InvoiceData.empty());
        invoice.addIssue(Severity.WARNING, ruleId, message);
        invoice.applyValidationResult();
        return invoices.save(invoice);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findIdByDocument(UUID tenantId, UUID documentId) {
        return invoices.findByTenantIdAndDocumentId(tenantId, documentId).map(Invoice::getId);
    }
}