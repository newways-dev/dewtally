package com.kate.dewtally.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full view of one invoice: header data, lines and validation issues.
 * The version must be sent back with a correction (optimistic locking).
 */
public record InvoiceDetailsResponse(
        UUID id,
        UUID documentId,
        Long version,
        InvoiceStatus status,
        ExtractionMethod extractionMethod,
        BigDecimal extractionConfidence,
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        String currency,
        String supplierName,
        String supplierVatId,
        String buyerReference,
        String iban,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal grossAmount,
        BigDecimal allowanceTotal,
        BigDecimal chargeTotal,
        Instant createdAt,
        List<Line> lines,
        List<Issue> issues
) {

    // Must be called inside a transaction: lines and issues are loaded lazily.
    public static InvoiceDetailsResponse from(Invoice invoice) {
        return new InvoiceDetailsResponse(
                invoice.getId(),
                invoice.getDocumentId(),
                invoice.getVersion(),
                invoice.getStatus(),
                invoice.getExtractionMethod(),
                invoice.getExtractionConfidence(),
                invoice.getInvoiceNumber(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getCurrency(),
                invoice.getSupplierName(),
                invoice.getSupplierVatId(),
                invoice.getBuyerReference(),
                invoice.getIban(),
                invoice.getNetAmount(),
                invoice.getTaxAmount(),
                invoice.getGrossAmount(),
                invoice.getAllowanceTotal(),
                invoice.getChargeTotal(),
                invoice.getCreatedAt(),
                invoice.getLines().stream().map(Line::from).toList(),
                invoice.getIssues().stream().map(Issue::from).toList());
    }

    public record Line(
            int lineNo,
            String description,
            BigDecimal quantity,
            String unitCode,
            BigDecimal unitPrice,
            BigDecimal netAmount,
            BigDecimal taxRate
    ) {
        static Line from(InvoiceLine line) {
            return new Line(
                    line.getLineNo(),
                    line.getDescription(),
                    line.getQuantity(),
                    line.getUnitCode(),
                    line.getUnitPrice(),
                    line.getNetAmount(),
                    line.getTaxRate());
        }
    }

    public record Issue(Severity severity, String ruleId, String message) {
        static Issue from(ValidationIssue issue) {
            return new Issue(issue.getSeverity(), issue.getRuleId(), issue.getMessage());
        }
    }
}