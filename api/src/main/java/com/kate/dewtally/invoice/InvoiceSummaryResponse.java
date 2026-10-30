package com.kate.dewtally.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the invoice list: what the table shows, without lines.
 *
 * @param issueCount number of findings (errors and warnings) on the invoice
 */
public record InvoiceSummaryResponse(
        UUID id,
        InvoiceStatus status,
        String invoiceNumber,
        String supplierName,
        String supplierVatId,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal grossAmount,
        String currency,
        ExtractionMethod extractionMethod,
        BigDecimal extractionConfidence,
        int issueCount,
        Instant createdAt
) {

    // Must be called inside a transaction: issues are loaded lazily.
    static InvoiceSummaryResponse from(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getId(),
                invoice.getStatus(),
                invoice.getInvoiceNumber(),
                invoice.getSupplierName(),
                invoice.getSupplierVatId(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getGrossAmount(),
                invoice.getCurrency(),
                invoice.getExtractionMethod(),
                invoice.getExtractionConfidence(),
                invoice.getIssues().size(),
                invoice.getCreatedAt());
    }
}