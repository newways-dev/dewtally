package com.kate.dewtally.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSummaryResponse(
        UUID id,
        InvoiceStatus status,
        String invoiceNumber,
        String supplierName,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal grossAmount,
        String currency,
        Instant createdAt
) {

    static InvoiceSummaryResponse from(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getId(),
                invoice.getStatus(),
                invoice.getInvoiceNumber(),
                invoice.getSupplierName(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getGrossAmount(),
                invoice.getCurrency(),
                invoice.getCreatedAt());
    }
}