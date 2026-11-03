package com.kate.dewtally.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full view of one invoice: header data, lines, VAT breakdown and validation issues.
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
        List<Vat> vatBreakdown,
        List<Issue> issues
) {

    // Must be called inside a transaction: lines, VAT breakdown and issues are loaded lazily.
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
                invoice.getVatBreakdown().stream().map(Vat::from).toList(),
                invoice.getIssues().stream().map(Issue::from).toList());
    }

    public record Line(
            int lineNo,
            String description,
            BigDecimal quantity,
            String unitCode,
            BigDecimal unitPrice,
            BigDecimal netAmount,
            BigDecimal taxRate,
            String taxCategory
    ) {
        static Line from(InvoiceLine line) {
            return new Line(
                    line.getLineNo(),
                    line.getDescription(),
                    line.getQuantity(),
                    line.getUnitCode(),
                    line.getUnitPrice(),
                    line.getNetAmount(),
                    line.getTaxRate(),
                    line.getTaxCategory());
        }
    }

    /**
     * One row of the VAT breakdown: category, rate, net amount and VAT.
     */
    public record Vat(
            String category,
            BigDecimal rate,
            BigDecimal taxableAmount,
            BigDecimal taxAmount,
            String exemptionReason
    ) {
        static Vat from(VatSubtotal subtotal) {
            return new Vat(
                    subtotal.category(),
                    subtotal.rate(),
                    subtotal.taxableAmount(),
                    subtotal.taxAmount(),
                    subtotal.exemptionReason());
        }
    }

    public record Issue(Severity severity, String ruleId, String message) {
        static Issue from(ValidationIssue issue) {
            return new Issue(issue.getSeverity(), issue.getRuleId(), issue.getMessage());
        }
    }
}